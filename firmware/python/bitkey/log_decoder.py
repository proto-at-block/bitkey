"""Host-side decoder for tokenized firmware UART log frames.

Wire format (decoded, before COBS):
    [ magic 0xBF | type (1) | level (1) | payload | crc16-ccitt LE (2) ]
COBS-encoded with a trailing 0x00 frame delimiter.

Types:
  0x01 compact log: payload is a CBOR array `[log_id, *args]` from Memfault's
                    compact log serializer; rendered against the ELF's
                    `log_fmt` section.
  0x02 raw text:    payload is UTF-8 bytes (no NUL).

Bytes between 0x00 delimiters that fail COBS decode or don't start with the
0xBF magic are treated as legacy ASCII output and printed verbatim — this
keeps boot-ROM, panic, and other non-tokenized streams readable on the same
UART.
"""

from __future__ import annotations

import argparse
import os
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterator, Optional, Union

import cbor2
from mflt_compact_log import (
    CompactLogDecodeError,
    CompactLogDecoder,
    NormalCompactLog,
    TruncatedCompactLog,
)

from bitkey.cobs import CobsDecoder, CobsError
from bitkey.log_tokens import TokenDatabase, find_elfs_by_build_id, find_elfs_by_build_ids

MAGIC = 0xBF
TYPE_COMPACT = 0x01
TYPE_RAW = 0x02
TYPE_BUILD_ID = 0x10

BUILD_ID_LEN = 20

LEVEL_NAMES = {0: "DEBUG", 1: "INFO ", 2: "WARN ", 3: "ERROR"}

# ANSI colors for tty output.
_COLOR = {
    "DEBUG": "\033[0;36m",
    "INFO ": "\033[0;32m",
    "WARN ": "\033[0;33m",
    "ERROR": "\033[0;31m",
}
_RESET = "\033[0m"


@dataclass
class CompactFrame:
    level: int
    log_id: int
    args: list


@dataclass
class RawFrame:
    level: int
    text: bytes


@dataclass
class BuildIdFrame:
    """Boot-time announcement of the firmware's 20-byte Memfault build ID."""
    level: int
    build_id: bytes


@dataclass
class BadFrame:
    raw: bytes
    reason: str


@dataclass
class Passthrough:
    """Bytes that aren't part of a valid frame — printed as ASCII."""
    raw: bytes


ParsedItem = Union[CompactFrame, RawFrame, BuildIdFrame, BadFrame, Passthrough]


def crc16_ccitt(data: bytes, init: int = 0x0000) -> int:
    """CRC-16/CCITT (xmodem) — matches firmware's memfault_crc16_ccitt_compute."""
    crc = init
    for b in data:
        crc ^= b << 8
        for _ in range(8):
            if crc & 0x8000:
                crc = (crc << 1) ^ 0x1021
            else:
                crc <<= 1
            crc &= 0xFFFF
    return crc


class FrameParser:
    """Streaming parser: feed bytes, yield ParsedItems at each frame boundary."""

    # Largest a valid encoded frame can be (matches firmware's
    # COBS_TINYFRAME_SAFE_BUFFER_SIZE = 256). When the inter-delimiter buffer
    # exceeds this size without seeing a 0x00 it definitely isn't a tokenized
    # frame, so we emit it as passthrough text. This keeps high-rate printf
    # output (e.g. 120 Hz mfgtest touch coords) streaming live instead of
    # accumulating indefinitely in `_buf` while the user sees nothing.
    MAX_FRAME_SIZE = 256

    def __init__(self):
        self._buf = bytearray()

    def feed(self, data: bytes) -> Iterator[ParsedItem]:
        for b in data:
            if b == 0x00:
                if self._buf:
                    yield from self._parse(bytes(self._buf))
                    self._buf.clear()
            else:
                self._buf.append(b)
                if len(self._buf) >= self.MAX_FRAME_SIZE:
                    yield Passthrough(bytes(self._buf))
                    self._buf.clear()

    def flush(self) -> Iterator[ParsedItem]:
        """Emit any remaining buffered bytes as passthrough text."""
        if self._buf:
            yield Passthrough(bytes(self._buf))
            self._buf.clear()

    @staticmethod
    def _parse(encoded: bytes) -> Iterator[ParsedItem]:
        # Try to interpret `encoded` (without delimiter) as a COBS frame.
        try:
            decoded = CobsDecoder.decode(encoded + b"\x00")
        except CobsError:
            yield Passthrough(encoded)
            return

        # Smallest valid frame = 1 magic + 1 type + 1 level + 0 payload + 2 crc = 5 B.
        if len(decoded) < 5 or decoded[0] != MAGIC:
            yield Passthrough(encoded)
            return

        crc_received = decoded[-2] | (decoded[-1] << 8)
        crc_computed = crc16_ccitt(decoded[:-2])
        if crc_received != crc_computed:
            yield BadFrame(raw=encoded,
                           reason=f"crc mismatch (got 0x{crc_received:04x}, "
                                  f"computed 0x{crc_computed:04x})")
            return

        frame_type = decoded[1]
        level = decoded[2]
        payload = decoded[3:-2]

        if frame_type == TYPE_COMPACT:
            try:
                obj = cbor2.loads(payload)
            except Exception as e:
                yield BadFrame(raw=encoded, reason=f"cbor decode failed: {e}")
                return
            if not isinstance(obj, list) or not obj:
                yield BadFrame(raw=encoded, reason="compact payload not a non-empty list")
                return
            log_id = obj[0]
            # log_id must be an int — _render_compact formats it as `0x%08x`,
            # which would TypeError on (e.g.) a corrupted frame whose first
            # CBOR element is a string. Drop to BadFrame so the reader thread
            # keeps running.
            if not isinstance(log_id, int):
                yield BadFrame(raw=encoded,
                               reason=f"compact log_id not an int (got {type(log_id).__name__})")
                return
            args = obj[1:]
            yield CompactFrame(level=level, log_id=log_id, args=args)
        elif frame_type == TYPE_RAW:
            yield RawFrame(level=level, text=payload)
        elif frame_type == TYPE_BUILD_ID:
            if len(payload) != BUILD_ID_LEN:
                yield BadFrame(raw=encoded,
                               reason=f"build-id payload wrong size ({len(payload)} != {BUILD_ID_LEN})")
                return
            yield BuildIdFrame(level=level, build_id=bytes(payload))
        else:
            yield BadFrame(raw=encoded, reason=f"unknown frame type 0x{frame_type:02x}")


class Renderer:
    """Turns ParsedItems into printable lines, looking up tokens against an ELF."""

    def __init__(self, db: Optional[TokenDatabase], use_color: bool = True,
                 auto_reload: bool = True,
                 auto_detect_index: Optional["dict[bytes, Path]"] = None,
                 auto_detect_roots: Optional["list[Path]"] = None,
                 short_paths: bool = True,
                 timestamps: bool = True,
                 hyperlinks: bool = True,
                 editor_uri: Optional[str] = None,
                 label: Optional[str] = None,
                 auto_relabel: bool = True):
        self.db = db
        self.use_color = use_color
        self.auto_reload = auto_reload
        self.short_paths = short_paths
        self.timestamps = timestamps
        # OSC 8 hyperlinks turn `(file:line)` into a clickable URI in
        # iTerm2 / Ghostty / Kitty / VS Code's terminal. Auto-disabled when
        # stdout is redirected to a non-tty so capture files stay clean.
        self.hyperlinks = hyperlinks and sys.stdout.isatty()
        # URI template for the click target. Placeholders: `{path}` (absolute
        # source path), `{line}` (line number). Default opens via macOS's
        # `file://` handler (Finder unless reconfigured); set to e.g.
        # `vscode://file/{path}:{line}` to jump straight into VS Code/Cursor.
        self.editor_uri = editor_uri or "file://{path}#L{line}"
        # Per-port label for multi-port mode. Rendered in front of every line.
        # When `auto_relabel` is True, the label is upgraded to a friendlier
        # name (e.g. `core`, `uxc`, `w1`) when auto-detect or auto-reload
        # attaches an ELF whose path identifies the platform — saves the user
        # from having to spell out `--port core=/dev/cu.usbserial-…`.
        self.label = label
        self.auto_relabel = auto_relabel
        # Index of {build_id: elf_path} for auto-detect: when a build-id
        # banner arrives and we don't already have a matching ELF, look it
        # up here and attach it transparently. `auto_detect_roots` (when set)
        # is rescanned on a miss so a freshly-built ELF in any of those
        # directories can be picked up mid-session without restarting the
        # monitor.
        self.auto_detect_index = auto_detect_index or {}
        self.auto_detect_roots = list(auto_detect_roots) if auto_detect_roots else []
        self._last_reindex_at: float = 0.0
        self._build_id_seen: Optional[bytes] = None
        self._loaded_mtime: Optional[float] = self._elf_mtime()
        # Tracks whether the next passthrough byte is at the start of a line
        # (so we know when to emit the line prefix). Starts True since we're
        # at the start of stdout.
        self._passthrough_at_line_start: bool = True

    # Don't rescan the build tree more often than this many seconds; protects
    # against a runaway loop if a device with a brand-new build_id keeps
    # banner'ing while the index is genuinely missing it.
    _REINDEX_MIN_INTERVAL = 1.0

    def _resolve_build_id(self, device_id: bytes):
        """Return (path, reindexed_count) for device_id.

        Tries the cached index first; on a miss, rescans every directory in
        `auto_detect_roots` (throttled) and tries again. `reindexed_count`
        is the new index size when a rescan happened, else None.
        """
        path = self.auto_detect_index.get(device_id)
        if path is not None or not self.auto_detect_roots:
            return path, None

        import time
        now = time.time()
        if now - self._last_reindex_at < self._REINDEX_MIN_INTERVAL:
            return None, None
        self._last_reindex_at = now
        # Mutate the existing dict in place rather than rebinding so that other
        # renderers sharing the same dict (multi-port mode) also see the new
        # entries — otherwise each port reindexes independently after the first
        # miss, wasting work and printing redundant "[reindexed: …]" banners.
        new_index = find_elfs_by_build_ids(self.auto_detect_roots)
        self.auto_detect_index.clear()
        self.auto_detect_index.update(new_index)
        return self.auto_detect_index.get(device_id), len(self.auto_detect_index)

    def _elf_mtime(self) -> Optional[float]:
        if self.db is None:
            return None
        try:
            return self.db.elf_path.stat().st_mtime
        except OSError:
            return None

    # Path-component → friendly platform label. We match against the lowered
    # ELF path's components delimited by `/`, with the platform string flanked
    # by `/`s, so a user whose `$HOME` happens to contain "w1" doesn't trigger
    # a misclassification. Order matters: `w3-core` / `w3-uxc` are matched
    # before `w1` so the more specific tag wins.
    _LABEL_HINTS = (("/w3-core/", "core"), ("/w3-uxc/", "uxc"), ("/w1/", "w1"))

    def _maybe_relabel_from_elf(self, elf_path: Path) -> None:
        """If the ELF path identifies a known platform, upgrade self.label."""
        if not self.auto_relabel:
            return
        name = str(elf_path).lower()
        for needle, friendly in self._LABEL_HINTS:
            if needle in name:
                self.label = friendly
                return

    def _maybe_reload_elf(self) -> bool:
        """Re-read the ELF if its mtime has advanced. Returns True on reload.

        Triggered from the build-id banner path: every device reboot that
        emits the banner is a natural sync point where reflashing fresh
        firmware should pull in the matching fresh token table.
        """
        if not self.auto_reload or self.db is None:
            return False
        new_mtime = self._elf_mtime()
        if new_mtime is None or new_mtime == self._loaded_mtime:
            return False
        self.db = TokenDatabase(self.db.elf_path)
        self._loaded_mtime = new_mtime
        # Clear so the upcoming match check renders a fresh banner.
        self._build_id_seen = None
        # Reloaded ELF almost always has a different build-id; refresh the
        # auto-detect index so banners across other ports / future banners on
        # this port hit the new entry without forcing a 1Hz reindex miss.
        if self.auto_detect_roots:
            new_index = find_elfs_by_build_ids(self.auto_detect_roots)
            self.auto_detect_index.clear()
            self.auto_detect_index.update(new_index)
        return True

    def emit(self, item: ParsedItem, out) -> None:
        """Write an item to `out` (a text stream like sys.stdout).

        Frames render as a single line followed by `\\r\\n`, prefixed with
        timestamp/label as configured. Passthrough text is written verbatim
        — preserving any newlines the device emitted — so the firmware shell
        prompt, command echo, and other unframed output flow through with
        the same byte-for-byte layout the device intended (timestamp/label
        prefix is applied at every line boundary inside the chunk).
        """
        if isinstance(item, Passthrough):
            try:
                text = item.raw.decode("utf-8", errors="replace")
            except Exception:
                return
            out.write(self._prefix_passthrough(text))
            out.flush()
            return

        line: Optional[str]
        if isinstance(item, CompactFrame):
            line = self._render_compact(item)
        elif isinstance(item, RawFrame):
            line = self._render_raw(item)
        elif isinstance(item, BuildIdFrame):
            line = self._render_build_id(item)
        elif isinstance(item, BadFrame):
            line = self._render_bad(item)
        else:
            line = None
        if line is not None:
            out.write(self._line_prefix() + line + "\r\n")
            out.flush()

    def _line_prefix(self) -> str:
        """Compose the per-line prefix from timestamp + label settings."""
        parts = []
        if self.timestamps:
            ts = _monotonic_since_start()
            parts.append(f"[+{ts:7.3f}]")
        if self.label is not None:
            parts.append(f"[{self.label}]")
        if not parts:
            return ""
        return " ".join(parts) + " "

    def _prefix_passthrough(self, text: str) -> str:
        """Apply the line prefix to each newline-terminated line in `text`.

        Passthrough output (shell prompt, printf, command echo) arrives in
        arbitrary chunks; we emit a prefix at the start and after every \\n
        that isn't the last byte, so each rendered line gets its own
        timestamp/label without spurious prefixes mid-line.
        """
        prefix = self._line_prefix()
        if not prefix:
            return text
        # Prefix at the very start if buffer is fresh (use a sticky flag).
        # Important: clear the flag after prepending so that subsequent chunks
        # arriving on the same line (e.g. one byte per keystroke as the device
        # echoes input) don't each get their own timestamp prefix.
        if self._passthrough_at_line_start:
            text = prefix + text
            self._passthrough_at_line_start = False
        # After every newline, the next character starts a new line and needs
        # its own prefix. Handle both internal newlines (split mid-chunk) and
        # the tail (the unterminated start-of-line at the end of this chunk).
        if "\n" in text:
            head, _, tail = text.rpartition("\n")
            if head:
                head = head.replace("\n", "\n" + prefix)
            text = head + "\n" + (prefix + tail if tail else "")
            # Sticky flag is True iff the chunk ended with a newline — in that
            # case the next chunk starts at column 0 and needs a prefix.
            self._passthrough_at_line_start = (tail == "")
        return text

    def _level_name(self, level: int) -> str:
        return LEVEL_NAMES.get(level, f"L{level}")

    def _format_filename(self, name: str) -> str:
        if self.short_paths:
            # Path() handles both `/` and `\` so this works regardless of
            # which slash the firmware embedded via __FILE__.
            return Path(name).name
        return name

    def _format_file_line(self, raw_path: str, line: int) -> str:
        """Compose `(file:line)` for compact-log output.

        When OSC 8 hyperlinks are enabled and we can locate the source on
        disk, wrap the displayed text in a `file://` URI clickable in
        modern terminals (iTerm2, Ghostty, Kitty, VS Code's terminal).
        Falls back to plain text if the source isn't reachable.
        """
        display = f"{self._format_filename(raw_path)}:{line}"
        if not self.hyperlinks or self.db is None:
            return f"({display})"
        abs_src = self._resolve_source_path(raw_path)
        if abs_src is None:
            return f"({display})"
        try:
            uri = self.editor_uri.format(path=abs_src, line=line)
        except (KeyError, IndexError, ValueError):
            uri = f"file://{abs_src}#L{line}"
        # Refuse to wrap in OSC 8 if either the URI or the displayed text
        # contains a control byte (< 0x20) — a corrupted or malicious __FILE__
        # could otherwise break out of the OSC 8 escape and inject arbitrary
        # terminal sequences into a tee'd capture or a shared screen.
        if any(ord(c) < 0x20 for c in uri) or any(ord(c) < 0x20 for c in display):
            return f"({display})"
        return f"({_osc8_link(uri, display)})"

    def _resolve_source_path(self, raw_path: str) -> Optional[Path]:
        """Map a __FILE__-style path to an absolute source file on disk.

        Format-string paths are recorded as the compiler saw them, which is
        typically relative to the meson build directory
        (`firmware/build/firmware/<platform>/`). The ELF lives a few subdirs
        deeper than that, so we walk up from the ELF parent and try each
        ancestor as a candidate base; the first one that resolves to an
        existing file wins.

        Resolved paths are bounded to the ELF's repo root (the nearest ancestor
        containing a `.git` marker) — a corrupt or malicious __FILE__ embedding
        e.g. `/etc/passwd` won't get rendered as a clickable hyperlink.
        """
        if self.db is None:
            return None
        repo_root = self._repo_root_for_elf()
        p = Path(raw_path)
        candidate: Optional[Path] = None
        if p.is_absolute():
            candidate = p if p.exists() else None
        else:
            base = self.db.elf_path.parent
            for _ in range(8):
                try:
                    c = (base / p).resolve()
                except OSError:
                    return None
                if c.exists():
                    candidate = c
                    break
                if base == base.parent:
                    break
                base = base.parent
        if candidate is None:
            return None
        if repo_root is not None:
            try:
                candidate.relative_to(repo_root)
            except ValueError:
                return None
        return candidate

    def _repo_root_for_elf(self) -> Optional[Path]:
        """Cached `.git`-rooted ancestor of the loaded ELF, or None."""
        if self.db is None:
            return None
        cached = getattr(self, "_cached_repo_root", None)
        if cached is not None and cached[0] == self.db.elf_path:
            return cached[1]
        root: Optional[Path] = None
        cur = self.db.elf_path.parent
        for _ in range(16):
            if (cur / ".git").exists():
                root = cur
                break
            if cur == cur.parent:
                break
            cur = cur.parent
        self._cached_repo_root = (self.db.elf_path, root)
        return root

    def _color_wrap(self, level_name: str, text: str) -> str:
        if not self.use_color:
            return text
        c = _COLOR.get(level_name, "")
        return f"{c}{text}{_RESET}" if c else text

    def _render_compact(self, frame: CompactFrame) -> str:
        level_name = self._level_name(frame.level)
        if self.db is None:
            return self._color_wrap(
                level_name,
                f"[{level_name}] (no-elf) token=0x{frame.log_id:08x} args={frame.args}",
            )
        entry = self.db.lookup(frame.log_id)
        if entry is None:
            return self._color_wrap(
                level_name,
                f"[{level_name}] (??) token=0x{frame.log_id:08x} args={frame.args}",
            )

        file_line = self._format_file_line(entry.filename, entry.line)
        try:
            unpacked = CompactLogDecoder.unpack_compact_log([frame.log_id, *frame.args])
        except Exception as e:
            return self._color_wrap(
                level_name,
                f"[{level_name}] {file_line} "
                f"unpack failed: {e}; raw args={frame.args}",
            )

        if isinstance(unpacked, TruncatedCompactLog):
            from mflt_compact_log import log_fmt as _lf
            info = _lf.LogFormatInfo(
                filename=entry.filename, line=entry.line,
                n_args=entry.n_args, fmt=entry.fmt,
            )
            text = CompactLogDecoder.render_truncated_log(info, unpacked)
            return self._color_wrap(
                level_name,
                f"[{level_name}] {file_line} {text}",
            )

        try:
            decoded_bytes = CompactLogDecoder.decode(entry.fmt, unpacked.va_args)
            decoded = decoded_bytes.decode("utf-8", errors="replace")
        except CompactLogDecodeError as e:
            decoded = f"<decode error: {e}; fmt={entry.fmt!r} args={unpacked.va_args}>"

        return self._color_wrap(
            level_name,
            f"[{level_name}] {file_line} {decoded}",
        )

    def _render_raw(self, frame: RawFrame) -> str:
        level_name = self._level_name(frame.level)
        try:
            text = frame.text.decode("utf-8", errors="replace").rstrip("\r\n")
        except Exception:
            text = repr(frame.text)
        return self._color_wrap(level_name, f"[{level_name}] {text}")

    def _render_bad(self, frame: BadFrame) -> str:
        return f"[badframe {frame.reason}] {frame.raw.hex()}"

    def _render_build_id(self, frame: BuildIdFrame) -> str:
        """Cross-check the device's build ID against the loaded ELF.

        Three layered behaviors, in order of precedence on each banner:
          1. Auto-reload: if the loaded ELF's mtime has advanced (a rebuild
             happened), pull in the fresh token table.
          2. Auto-detect: if no ELF is loaded yet, or the loaded one doesn't
             match the device's build ID, search the auto-detect index for
             one that does and attach it transparently.
          3. Mismatch warning: if we still don't have a matching ELF, render
             a loud red banner so subsequent compact logs aren't silently
             misinterpreted.
        """
        device_id = frame.build_id
        device_hex = device_id.hex()
        prefix = ""

        if self._maybe_reload_elf() and self.db is not None:
            prefix += f"[elf reloaded: {self.db.elf_path.name}, {len(self.db)} tokens] "
            self._maybe_relabel_from_elf(self.db.elf_path)

        # Auto-detect: pull in the matching ELF from the index when nothing's
        # loaded yet or what's loaded doesn't match. _resolve_build_id will
        # rescan auto_detect_root on a miss (throttled), so a build that
        # finished after the monitor started still gets picked up.
        if self.db is None or (self.db.build_id is not None
                               and self.db.build_id != device_id):
            candidate, reindexed_count = self._resolve_build_id(device_id)
            if reindexed_count is not None:
                prefix += f"[reindexed: {reindexed_count} ELFs] "
            if candidate is not None and (self.db is None or candidate != self.db.elf_path):
                self.db = TokenDatabase(candidate)
                self._loaded_mtime = self._elf_mtime()
                prefix += f"[auto-detected: {candidate}, {len(self.db)} tokens] "
                self._maybe_relabel_from_elf(candidate)

        if self._build_id_seen == device_id:
            return f"{prefix}[build-id] device={device_hex} (already seen)"
        self._build_id_seen = device_id

        if self.db is None:
            hint = ""
            if self.auto_detect_index:
                hint = (f" (none of {len(self.auto_detect_index)} indexed ELFs match; "
                        "rebuild + reflash, or pass --elf)")
            return f"{prefix}[build-id] device={device_hex} (no ELF loaded for verification){hint}"

        elf_id = self.db.build_id
        if elf_id is None:
            return (f"{prefix}[build-id] device={device_hex} "
                    f"elf={self.db.elf_path.name}: no build-id symbol "
                    "(unsigned ELF? token decode may still work)")

        if elf_id == device_id:
            return f"{prefix}[build-id] device={device_hex} matches ELF ✓"

        # Mismatch — this is the silent-data-corruption case we exist to catch.
        suggestion = ""
        candidate = self.auto_detect_index.get(device_id)
        if candidate is not None and candidate != self.db.elf_path:
            suggestion = f" Did you mean --elf {candidate}?"

        warning = (f"⚠ ELF MISMATCH ⚠ device={device_hex} "
                   f"elf={elf_id.hex()} ({self.db.elf_path.name}); "
                   "tokenized lines below WILL DECODE WRONG."
                   f"{suggestion}")
        if self.use_color:
            warning = f"\033[1;31m{warning}\033[0m"
        return f"{prefix}{warning}"


# ---------------------------------------------------------------------------
# CLI entry point: `python3 -m bitkey.log_decoder --port ... --elf ...`
# Also used by `inv log.monitor` (firmware/tasks/log.py).

# Ctrl-C (ETX, 0x03) — exits the monitor. The stdin pump runs the tty in raw
# mode and detects this byte directly rather than relying on signal delivery,
# so it works the same regardless of terminal/shell/invoke quirks.
_EXIT_BYTE = 0x03

# Monotonic clock anchor for timestamp prefixes — captured the first time we
# need a timestamp so the value is "seconds since the monitor started".
import time as _time
_T0: Optional[float] = None


def _monotonic_since_start() -> float:
    global _T0
    if _T0 is None:
        _T0 = _time.monotonic()
    return _time.monotonic() - _T0


def _osc8_link(uri: str, text: str) -> str:
    """Wrap `text` in an OSC 8 hyperlink to `uri`. iTerm2 / Kitty / VS Code's
    integrated terminal / Ghostty render this as clickable; older terminals
    drop the escape codes silently. ESC ] 8 ; ; URI ST text ESC ] 8 ; ; ST."""
    return f"\x1b]8;;{uri}\x1b\\{text}\x1b]8;;\x1b\\"


class _SerialWriter:
    """Thread-safe holder for a pyserial Serial object.

    The stdin pump uses this to forward keystrokes; until the serial port has
    finished opening on the main thread, writes are silently dropped instead
    of crashing. This lets us start the pump (and put the tty in raw mode)
    *before* opening the serial port — critical because USB serial open can
    block for seconds, during which we still want Ctrl-C to be honored.
    """

    def __init__(self):
        self._ser = None

    def attach(self, ser):
        self._ser = ser

    def write(self, data):
        ser = self._ser
        if ser is not None:
            ser.write(data)


def _stdin_pump(writer, stop_event):
    """Forward keystrokes from the tty to the serial writer; exit on Ctrl-C.

    Puts stdin into fully-raw termios so we see every byte the user types,
    including Ctrl-C as `0x03`. That byte trips the exit and lets the main
    thread join cleanly. Forwarding bytes through to the serial port keeps
    the firmware shell interactive.
    """
    import atexit
    import os
    import select
    import termios

    if not sys.stdin.isatty():
        return
    fd = sys.stdin.fileno()

    old_attrs = termios.tcgetattr(fd)
    # Belt-and-suspenders: the daemon thread's `finally` won't run if the main
    # process exits via SIGKILL or an uncaught exception above us. atexit fires
    # in both clean shutdown and most unhandled-exception paths, so the user's
    # terminal isn't left in raw mode and unable to echo characters.
    def _restore():
        try:
            termios.tcsetattr(fd, termios.TCSADRAIN, old_attrs)
        except Exception:
            pass
    atexit.register(_restore)
    try:
        new_attrs = termios.tcgetattr(fd)
        # Raw on the INPUT side only: forward every keystroke as a byte and
        # let the pump detect Ctrl-C explicitly. Don't touch oflag — keeping
        # OPOST + ONLCR set means the terminal still translates bare `\n`
        # from the device (firmware shell prompts) to `\r\n` for display, so
        # we don't get the staircase / tab-down artifact in terminals that
        # honor termios strictly.
        new_attrs[0] &= ~(termios.IGNBRK | termios.BRKINT | termios.PARMRK |
                          termios.ISTRIP | termios.INLCR | termios.IGNCR |
                          termios.ICRNL | termios.IXON)
        new_attrs[3] &= ~(termios.ECHO | termios.ECHONL | termios.ICANON |
                          termios.ISIG | termios.IEXTEN)
        new_attrs[6][termios.VMIN] = 1
        new_attrs[6][termios.VTIME] = 0
        termios.tcsetattr(fd, termios.TCSADRAIN, new_attrs)

        while not stop_event.is_set():
            ready, _, _ = select.select([fd], [], [], 0.1)
            if not ready:
                continue
            try:
                data = os.read(fd, 256)
            except (OSError, InterruptedError):
                continue
            if not data:
                continue
            if _EXIT_BYTE in data:
                stop_event.set()
                break
            # macOS / most Linux terminals send ASCII DEL (0x7f) for the
            # Backspace key, but the firmware shell only recognises BS
            # (0x08, see shell.c). Translate so backspace edits the line as
            # users expect. Multi-byte escape sequences (arrow keys etc.)
            # don't contain a standalone 0x7f, so this is safe to do
            # byte-wise.
            data = data.replace(b"\x7f", b"\x08")
            writer.write(data)
    finally:
        termios.tcsetattr(fd, termios.TCSADRAIN, old_attrs)


@dataclass
class PortSpec:
    """Everything one serial port needs to be decoded independently."""
    label: str
    path: str
    baud: int
    parser: FrameParser
    renderer: Renderer
    tee_path: Optional[Path] = None


class _BroadcastWriter:
    """Forwards stdin keystrokes to multiple `_SerialWriter`s at once."""

    def __init__(self, writers):
        self._writers = list(writers)

    def write(self, data):
        for w in self._writers:
            w.write(data)


_ANSI_CSI_RE = re.compile(r"\x1b\[[0-9;?]*[a-zA-Z]")
# OSC 8 hyperlink: ESC ] 8 ; ; URI ESC \  text  ESC ] 8 ; ; ESC \
_OSC8_RE = re.compile(r"\x1b\]8;;[^\x1b]*\x1b\\")


def _strip_ansi(s: str) -> str:
    """Strip ANSI CSI escapes and OSC 8 hyperlink wrappers (preserving the
    visible text inside the hyperlink). Used to keep `monitor.log` plain so
    `cat`/`grep`/`less` produce readable output without `-R`."""
    return _ANSI_CSI_RE.sub("", _OSC8_RE.sub("", s))


class _LockedTextOut:
    """Locked wrapper around `sys.stdout` so reader threads don't interleave
    mid-line. Renderer.emit calls `.write()` then `.flush()` per line; we
    serialize each pair under a lock so output stays coherent even with
    multiple ports streaming simultaneously.

    Optionally tees the rendered stream to a log file (with ANSI escapes
    stripped) — this gives the user a plain-text record of the session by
    default, matching the legacy `inv monitor`'s `tee monitor.log`
    behavior."""

    def __init__(self, lock, log_file=None):
        self._lock = lock
        self._log_file = log_file

    def write(self, s):
        with self._lock:
            sys.stdout.write(s)
            if self._log_file is not None:
                try:
                    self._log_file.write(_strip_ansi(s))
                except Exception:
                    pass

    def flush(self):
        with self._lock:
            sys.stdout.flush()
            if self._log_file is not None:
                try:
                    self._log_file.flush()
                except Exception:
                    pass


def _run_port(spec: PortSpec, writer: _SerialWriter, stop_event,
              out: _LockedTextOut) -> None:
    """Read+decode loop for a single port. Runs in its own thread."""
    import serial as pyserial

    tee_fp = spec.tee_path.open("ab") if spec.tee_path else None
    sys.stderr.write(f"[{spec.label}] opening {spec.path} @ {spec.baud}...\r\n")
    sys.stderr.flush()

    ser = None
    err = None
    # Open in this same thread (we're already off the main thread) but still
    # poll stop_event between attempts so Ctrl-C aborts a hung open. We treat
    # opening as one shot here — if it hangs, Ctrl-C will set stop_event and
    # the next ser.read iteration won't run; the daemon thread dies on
    # process exit.
    try:
        ser = pyserial.Serial(spec.path, spec.baud, timeout=0.02)
    except Exception as e:
        err = e

    if ser is None:
        sys.stderr.write(f"[{spec.label}] failed to open {spec.path}: {err}\r\n")
        sys.stderr.flush()
        if tee_fp:
            tee_fp.close()
        # If a port fails, signal everyone — partial monitor sessions are
        # confusing.
        stop_event.set()
        return

    writer.attach(ser)
    sys.stderr.write(f"[{spec.label}] opened. decoding...\r\n")
    sys.stderr.flush()

    # A 256-byte COBS frame at 115200 baud is ~22 ms on the wire, so a 20 ms
    # ser.read timeout could fire mid-frame. Idle-flushing on every empty read
    # would dump the half-arrived frame as passthrough text and clear the
    # buffer, corrupting the still-incoming half. Instead, defer the
    # passthrough flush until we've been quiet for `_IDLE_FLUSH_AFTER` so a
    # buffered partial frame has time to complete before we give up on it.
    #
    # Tuning: the threshold has to sit above the worst-case frame transmit
    # time (~22 ms) but as low as possible otherwise — it sets the floor on
    # keystroke-echo latency when the firmware is otherwise idle (no log
    # frames arriving to terminate the buffer via a 0x00 delimiter). 30 ms
    # gives ~36% margin over frame transmit time while keeping echo lag
    # imperceptible (~50 ms end-to-end including the 20 ms ser.read poll).
    _IDLE_FLUSH_AFTER = 0.03  # seconds
    last_byte_at = 0.0
    needs_idle_flush = False
    try:
        while not stop_event.is_set():
            try:
                chunk = ser.read(4096)
            except OSError:
                break
            if chunk:
                if tee_fp:
                    tee_fp.write(chunk)
                    tee_fp.flush()
                for item in spec.parser.feed(chunk):
                    spec.renderer.emit(item, out)
                last_byte_at = _time.monotonic()
                needs_idle_flush = True
            elif needs_idle_flush and (_time.monotonic() - last_byte_at) >= _IDLE_FLUSH_AFTER:
                for item in spec.parser.flush():
                    spec.renderer.emit(item, out)
                needs_idle_flush = False
    finally:
        for item in spec.parser.flush():
            spec.renderer.emit(item, out)
        if tee_fp:
            tee_fp.close()
        try:
            ser.close()
        except Exception:
            pass


def _stream_serial(specs, read_only: bool = False,
                   log_file: Optional[Path] = None):
    """Run one or more serial ports in parallel.

    Each `PortSpec` gets its own reader thread (open + read + parse + emit).
    Stdout is shared under a lock so lines from different ports don't
    interleave mid-line. The stdin pump forwards keystrokes to all ports
    when interactive (single-port: as before; multi-port: defaults to
    read-only since broadcasting echoes back from each port).

    `log_file`, when provided, is opened as a plain-text capture of the
    rendered session (ANSI escapes stripped) — same role as the legacy
    `inv monitor`'s `tee monitor.log`.
    """
    import threading

    if isinstance(specs, PortSpec):
        specs = [specs]

    stop_event = threading.Event()
    out_lock = threading.Lock()
    log_fp = None
    if log_file is not None:
        try:
            log_file = Path(log_file).expanduser()
            log_file.parent.mkdir(parents=True, exist_ok=True)
            log_fp = log_file.open("w", buffering=1, encoding="utf-8",
                                   errors="replace")
            sys.stderr.write(f"(rendered session → {log_file})\r\n")
            sys.stderr.flush()
        except OSError as e:
            sys.stderr.write(f"(could not open log file {log_file}: {e})\r\n")
            sys.stderr.flush()
            log_fp = None
    locked_out = _LockedTextOut(out_lock, log_file=log_fp)
    writers = [_SerialWriter() for _ in specs]

    # Multi-port: don't forward stdin by default — broadcast would echo back
    # from every port, confusing.
    multi = len(specs) > 1
    do_pump = (not read_only) and (not multi)
    pump_thread = None
    if do_pump:
        pump_thread = threading.Thread(
            target=_stdin_pump, args=(writers[0], stop_event), daemon=True)
        pump_thread.start()
        sys.stderr.write("(press Ctrl-C to exit)\r\n")
        sys.stderr.flush()
    elif multi:
        sys.stderr.write(
            f"({len(specs)} ports — read-only mode; press Ctrl-C in this "
            "shell to exit)\r\n")
        sys.stderr.flush()

    # Spawn one reader thread per port.
    reader_threads = []
    for spec, w in zip(specs, writers):
        t = threading.Thread(target=_run_port,
                             args=(spec, w, stop_event, locked_out),
                             daemon=True)
        t.start()
        reader_threads.append(t)

    # In multi-port no-pump mode the main thread has nothing to do but block
    # on Ctrl-C / stop_event. KeyboardInterrupt is the normal exit path here
    # because we never put the tty in raw mode without the pump.
    try:
        while not stop_event.is_set():
            stop_event.wait(0.2)
    except KeyboardInterrupt:
        stop_event.set()
    finally:
        stop_event.set()
        if pump_thread is not None:
            pump_thread.join(timeout=0.5)
        for t in reader_threads:
            t.join(timeout=1.0)
        if log_fp is not None:
            try:
                log_fp.close()
            except Exception:
                pass


def _stream_stdin(parser: FrameParser, renderer: Renderer):
    """Read raw bytes from stdin (useful for piping captured streams)."""
    while True:
        chunk = sys.stdin.buffer.read(4096)
        if not chunk:
            break
        for item in parser.feed(chunk):
            renderer.emit(item, sys.stdout)
    for item in parser.flush():
        renderer.emit(item, sys.stdout)


def _parse_args(argv=None):
    p = argparse.ArgumentParser(
        prog="bitkey-log-decode",
        description="Decode tokenized firmware UART logs against an ELF.",
    )
    p.add_argument("--port", help="Serial port (e.g. /dev/cu.usbserial-XYZ).")
    p.add_argument("--baud", type=int, default=115200,
                   help="Serial baud rate (default: 115200).")
    p.add_argument("--elf", type=Path,
                   help="Firmware ELF for token lookup. If omitted, frames "
                        "are shown with raw token/args.")
    p.add_argument("--tee", type=Path,
                   help="Also write the raw byte stream to this file.")
    p.add_argument("--no-color", action="store_true",
                   help="Disable ANSI color output.")
    p.add_argument("--read-only", action="store_true",
                   help="Don't forward stdin keystrokes to the serial port.")
    p.add_argument("--no-elf-reload", action="store_true",
                   help="Don't auto-reload the ELF when its mtime changes. By default "
                        "a build-id banner from a freshly reflashed device triggers a "
                        "reload so the live token table matches the running firmware.")
    p.add_argument("--full-paths", action="store_true",
                   help="Show the full source path embedded by __FILE__ "
                        "(default: just the basename, e.g. `fpc_cmd.c`).")
    p.add_argument("--no-timestamps", action="store_true",
                   help="Don't prefix each line with a monotonic timestamp.")
    p.add_argument("--no-hyperlinks", action="store_true",
                   help="Don't render OSC 8 hyperlinks for `(file:line)`.")
    p.add_argument("--editor-uri", default=os.environ.get("BITKEY_LOG_EDITOR_URI"),
                   help="URI template for OSC 8 hyperlinks. Placeholders: "
                        "`{path}`, `{line}`. Defaults to `file://{path}#L{line}` "
                        "(opens in your default file:// handler — Finder on macOS "
                        "unless reconfigured). Try `vscode://file/{path}:{line}` "
                        "for VS Code/Cursor, `cursor://file/{path}:{line}` for "
                        "Cursor specifically, `subl://open?url=file://{path}&"
                        "line={line}` for Sublime, or "
                        "`txmt://open?url=file://{path}&line={line}` for TextMate.")
    p.add_argument("--stdin", action="store_true",
                   help="Read encoded bytes from stdin instead of a serial port.")
    return p.parse_args(argv)


def main(argv=None):
    args = _parse_args(argv)

    db = TokenDatabase(args.elf) if args.elf else None
    if db is not None:
        print(f"# loaded {len(db)} tokens from {args.elf}", file=sys.stderr)

    parser = FrameParser()
    use_color = not args.no_color and sys.stdout.isatty()
    renderer = Renderer(db, use_color=use_color, auto_reload=not args.no_elf_reload,
                        short_paths=not args.full_paths,
                        timestamps=not args.no_timestamps,
                        hyperlinks=not args.no_hyperlinks,
                        editor_uri=args.editor_uri)

    if args.stdin:
        _stream_stdin(parser, renderer)
        return 0

    if not args.port:
        print("error: --port is required (or pass --stdin)", file=sys.stderr)
        return 2

    spec = PortSpec(
        label=Path(args.port).name,
        path=args.port,
        baud=args.baud,
        parser=parser,
        renderer=renderer,
        tee_path=args.tee,
    )
    _stream_serial([spec], read_only=args.read_only)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
