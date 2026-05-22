"""Token database for tokenized firmware logs.

Wraps `mflt_compact_log.LogFormatElfSectionParser` to map a 32-bit log_id
(offset within the firmware ELF's `log_fmt` section) back to its original
`(filename, line, format string, n_args)` tuple.

The token database is keyed by ELF file path and lazily loaded on first
lookup. Multiple decoders can share a single database by passing the same
path. The database also exposes the ELF's Memfault-derived build ID so the
host decoder can verify the connected firmware matches the supplied ELF.
"""

from __future__ import annotations

import json
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Tuple

from elftools.elf.elffile import ELFFile
from mflt_compact_log import LogFormatElfSectionParser, log_fmt

# Directories we never descend into during candidate-root discovery, even
# at the immediate-child level. Bitkey worktree parents may have these
# alongside actual worktrees and we want to skip them quickly.
_PRUNE_DIRS = frozenset({
    ".git", ".gradle", ".hermit", ".idea", ".pytest_cache", ".venv",
    "__pycache__", "Library", "node_modules", "target", "third-party",
    "venv", "vendor",
})

# Bitkey signed firmware ELFs always live under `<root>/firmware/build/firmware/`.
# We only rglob those subtrees instead of the whole `build_root`, which keeps
# scans fast even when `build_root` is a worktree parent containing mobile
# apps, web sources, etc.
_BUILD_SUBPATH = Path("firmware/build/firmware")

MEMFAULT_BUILD_ID_LEN = 20
MEMFAULT_BUILD_ID_SYMBOL = "g_memfault_sdk_derived_build_id"

# Persisted cache of {abs_elf_path: (mtime, hex_build_id)}. Lets the auto-detect
# scan reuse build IDs we already extracted in earlier sessions, since
# pyelftools symbol lookup is ~150 ms per ELF and a worktree-parent scan
# touches 100+ files.
_CACHE_PATH = Path.home() / ".cache" / "bitkey" / "log_token_build_ids.json"


@dataclass(frozen=True)
class TokenEntry:
    log_id: int
    filename: str
    line: int
    fmt: str
    n_args: int


class TokenDatabase:
    """Lazy-loading token database backed by an ELF file."""

    def __init__(self, elf_path: Path):
        self.elf_path = Path(elf_path)
        self._mapping: Optional[Dict[int, log_fmt.LogFormatInfo]] = None
        self._build_id: Optional[bytes] = None
        self._build_id_loaded = False

    @property
    def mapping(self) -> Dict[int, log_fmt.LogFormatInfo]:
        if self._mapping is None:
            self._mapping = LogFormatElfSectionParser.get_mapping_from_elf_file(
                str(self.elf_path)
            )
        return self._mapping

    @property
    def build_id(self) -> Optional[bytes]:
        """Read the post-signing Memfault build ID from the ELF.

        Returns the 20-byte digest the firmware signer wrote into
        `g_memfault_sdk_derived_build_id`, or `None` if the symbol can't be
        found or read (unsigned ELF, missing symtab, etc.). Cached on first
        access.
        """
        if not self._build_id_loaded:
            self._build_id = _read_build_id_from_elf(self.elf_path)
            self._build_id_loaded = True
        return self._build_id

    def lookup(self, log_id: int) -> Optional[TokenEntry]:
        info = self.mapping.get(log_id)
        if info is None:
            return None
        return TokenEntry(
            log_id=log_id,
            filename=info.filename,
            line=info.line,
            fmt=info.fmt,
            n_args=info.n_args,
        )

    def __len__(self) -> int:
        return len(self.mapping)

    def __contains__(self, log_id: int) -> bool:
        return log_id in self.mapping


def find_elfs_by_build_ids(build_roots: Iterable[Path]) -> Dict[bytes, Path]:
    """Index every signed ELF under any of `build_roots`.

    Convenience over `find_elfs_by_build_id` for callers with multiple root
    directories (e.g. a user juggling worktrees that aren't all under one
    parent). Later roots overwrite entries from earlier roots on build-id
    collision; in practice content-derived build IDs don't collide so this
    only matters if the same ELF is reachable via two roots.
    """
    merged: Dict[bytes, Path] = {}
    for root in build_roots:
        merged.update(find_elfs_by_build_id(root))
    return merged


def find_elfs_by_build_id(build_root: Path) -> Dict[bytes, Path]:
    """Index every signed ELF under `build_root` by its 20-byte build ID.

    Used by the live decoder's auto-detect mode: when a device emits its
    build-id banner the renderer can look the build ID up here to find the
    matching ELF without the user having to specify `--elf` or `--platform`.

    Build IDs are content-derived (the firmware signer writes a SHA-1 of the
    metadata-section hash), so collisions across builds are vanishingly
    unlikely; on collision we keep the lexicographically-last path.

    Only `firmware/build/firmware/` subtrees are walked (see
    `_candidate_subtrees`), which keeps pointing at a worktree parent —
    e.g. `~/conductor/workspaces/<project>/` — fast even with several
    worktrees alongside large mobile/web/server trees. Build IDs are
    cached on disk by `(path, mtime)` so re-runs only re-parse ELFs that
    actually changed.
    """
    index: Dict[bytes, Path] = {}
    if not build_root.is_dir():
        return index

    elf_paths: list = []
    for subtree in _candidate_subtrees(build_root):
        elf_paths.extend(subtree.rglob("*.signed.elf"))

    cache = _load_build_id_cache()
    cache_dirty = False
    for elf in sorted(elf_paths):
        try:
            mtime = elf.stat().st_mtime
        except OSError:
            continue
        bid = _cache_lookup(cache, elf, mtime)
        if bid is None:
            bid = _read_build_id_from_elf(elf)
            if bid is not None:
                cache[str(elf)] = [mtime, bid.hex()]
                cache_dirty = True
        if bid is None:
            continue
        index[bid] = elf

    if cache_dirty:
        _save_build_id_cache(cache)
    return index


def _candidate_subtrees(build_root: Path):
    """Yield the directories we should rglob for signed ELFs.

    Bitkey signed firmware ELFs always live under `firmware/build/firmware/`,
    so we recognise three shapes the caller might pass and dispatch each
    cleanly without ever scanning unrelated trees:

      1. build_root IS `…/firmware/build/firmware/`     → rglob as-is
      2. build_root is a worktree root (has that path)  → that subdir
      3. build_root is a worktree parent (each child is)→ each child's subdir

    If none match, yield nothing — better to surface "0 ELFs found" than to
    quietly take 30+ seconds walking node_modules and friends.
    """
    seen: set = set()

    def _try(p: Path):
        if not p.is_dir():
            return None
        try:
            r = p.resolve()
        except OSError:
            return None
        if r in seen:
            return None
        seen.add(r)
        return r

    # Shape 1: build_root already ends in `.../firmware/build/firmware`.
    # Resolve first so relative paths (e.g. `build/firmware`) and symlinks
    # match too.
    try:
        resolved = build_root.resolve()
    except OSError:
        resolved = build_root
    parts = resolved.parts
    if len(parts) >= 3 and tuple(parts[-3:]) == _BUILD_SUBPATH.parts:
        m = _try(resolved)
        if m is not None:
            yield m
        return

    # Shape 2: worktree root.
    m = _try(build_root / _BUILD_SUBPATH)
    if m is not None:
        yield m
        return

    # Shape 3: worktree parent — dive one level.
    try:
        children = list(build_root.iterdir())
    except OSError:
        return
    for child in children:
        if not child.is_dir() or child.name in _PRUNE_DIRS:
            continue
        m = _try(child / _BUILD_SUBPATH)
        if m is not None:
            yield m


def _cache_lookup(cache: dict, elf: Path, mtime: float) -> Optional[bytes]:
    entry = cache.get(str(elf))
    if not entry:
        return None
    cached_mtime, hex_id = entry
    if cached_mtime != mtime:
        return None
    try:
        return bytes.fromhex(hex_id)
    except ValueError:
        return None


def _load_build_id_cache() -> dict:
    try:
        return json.loads(_CACHE_PATH.read_text())
    except (FileNotFoundError, ValueError, OSError):
        return {}


def _save_build_id_cache(cache: dict) -> None:
    """Atomic write so concurrent monitor sessions don't trash each other's
    cache and leave us re-parsing every ELF on the next start."""
    # Drop entries whose ELF no longer exists on disk so the cache doesn't grow
    # forever after `inv clean`.
    cache = {k: v for k, v in cache.items() if Path(k).exists()}
    try:
        _CACHE_PATH.parent.mkdir(parents=True, exist_ok=True)
        tmp_path = _CACHE_PATH.with_suffix(".tmp")
        tmp_path.write_text(json.dumps(cache, indent=2))
        os.replace(tmp_path, _CACHE_PATH)
    except OSError:
        pass  # cache is best-effort; never fail the scan over it


def _read_build_id_from_elf(elf_path: Path) -> Optional[bytes]:
    """Read `g_memfault_sdk_derived_build_id`'s storage bytes from a signed ELF.

    The Bitkey firmware signer writes a deterministic SHA-1 digest (the
    metadata-section hash) into this symbol. Reading it back gives us the same
    20-byte value the firmware reports at boot, so the host can compare.
    """
    try:
        with elf_path.open("rb") as f:
            elf = ELFFile(f)
            symtab = elf.get_section_by_name(".symtab")
            if symtab is None:
                return None
            matches = symtab.get_symbol_by_name(MEMFAULT_BUILD_ID_SYMBOL)
            if not matches:
                return None
            sym = matches[0]
            addr = sym["st_value"]
            size = sym["st_size"] or MEMFAULT_BUILD_ID_LEN

            # Find the loaded segment containing the symbol and convert its
            # virtual address to a file offset.
            for seg in elf.iter_segments():
                if seg.header["p_type"] != "PT_LOAD":
                    continue
                vbase = seg["p_vaddr"]
                if not (vbase <= addr < vbase + seg["p_filesz"]):
                    continue
                f.seek(addr - vbase + seg["p_offset"])
                return f.read(size)
    except Exception:
        return None
    return None
