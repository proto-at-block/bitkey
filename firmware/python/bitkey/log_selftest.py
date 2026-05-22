"""Self-test harness for tokenized logging.

Runs a battery of synthetic scenarios against the host decoder without
needing a physical device, so we can quickly validate parameter combos and
corner cases. Invoked via `inv log.selftest`.

Scenarios are functions decorated with `@scenario(name)`; each constructs a
Renderer + FrameParser, feeds synthetic bytes, and asserts on the rendered
output. Results print as a pass/fail table at the end.
"""

from __future__ import annotations

import io
import os
import shutil
import time
from pathlib import Path
from typing import Callable, List, Optional, Tuple

import cbor2

from bitkey.cobs import CobsEncoder
from bitkey.log_decoder import (BUILD_ID_LEN, FrameParser, MAGIC, Renderer,
                                TYPE_BUILD_ID, TYPE_COMPACT, TYPE_RAW,
                                crc16_ccitt)
from bitkey.log_tokens import (TokenDatabase, find_elfs_by_build_ids,
                               _CACHE_PATH)


# ─── synthetic frame builders ───────────────────────────────────────────────

def _frame(typ: int, level: int, payload: bytes) -> bytes:
    inner = bytes([MAGIC, typ, level]) + payload
    crc = crc16_ccitt(inner)
    return CobsEncoder.encode(inner + bytes([crc & 0xFF, (crc >> 8) & 0xFF]))


def compact_frame(log_id: int, args: list, level: int = 1) -> bytes:
    return _frame(TYPE_COMPACT, level, cbor2.dumps([log_id, *args]))


def raw_frame(text: bytes, level: int = 1) -> bytes:
    return _frame(TYPE_RAW, level, text)


def build_id_frame(build_id: bytes, level: int = 1) -> bytes:
    return _frame(TYPE_BUILD_ID, level, build_id)


def bad_crc_frame(log_id: int) -> bytes:
    """Build a frame with a deliberately wrong CRC."""
    payload = cbor2.dumps([log_id])
    inner = bytes([MAGIC, TYPE_COMPACT, 1]) + payload
    inner += bytes([0xDE, 0xAD])  # wrong CRC
    return CobsEncoder.encode(inner)


# ─── scenario plumbing ──────────────────────────────────────────────────────

_SCENARIOS: List[Tuple[str, Callable]] = []


def scenario(name: str):
    def deco(fn):
        _SCENARIOS.append((name, fn))
        return fn
    return deco


def _run_render(renderer: Renderer, parser: FrameParser, data: bytes) -> str:
    out = io.StringIO()
    for item in parser.feed(data):
        renderer.emit(item, out)
    for item in parser.flush():
        renderer.emit(item, out)
    return out.getvalue()


def _assert(cond: bool, msg: str) -> None:
    if not cond:
        raise AssertionError(msg)


# ─── scenarios ──────────────────────────────────────────────────────────────

@scenario("compact frame: known token decodes with file:line + args")
def _s(elf_path: Path):
    db = TokenDatabase(elf_path)
    r = Renderer(db, use_color=False, hyperlinks=False, timestamps=False)
    out = _run_render(r, FrameParser(), compact_frame(0x00000008, [42]))
    _assert("Match: 42" in out, f"missing decoded message: {out!r}")
    _assert("(fpc_cmd.c:69)" in out, f"missing file:line: {out!r}")
    _assert("[INFO ]" in out, f"missing level prefix: {out!r}")


@scenario("compact frame: unknown token renders fallback")
def _s(elf_path: Path):
    db = TokenDatabase(elf_path)
    r = Renderer(db, use_color=False, hyperlinks=False, timestamps=False)
    out = _run_render(r, FrameParser(), compact_frame(0xDEADBEEF, ["x", 1]))
    _assert("(??)" in out, f"missing unknown-token marker: {out!r}")
    _assert("0xdeadbeef" in out, f"missing token id: {out!r}")


@scenario("compact frame: no ELF renders no-elf marker")
def _s(elf_path: Path):
    r = Renderer(db=None, use_color=False, hyperlinks=False, timestamps=False)
    out = _run_render(r, FrameParser(), compact_frame(0x00000008, [42]))
    _assert("(no-elf)" in out, f"missing no-elf marker: {out!r}")


@scenario("raw frame: payload rendered verbatim with level prefix")
def _s(elf_path: Path):
    r = Renderer(db=None, use_color=False, hyperlinks=False, timestamps=False)
    out = _run_render(r, FrameParser(), raw_frame(b"hello world", level=3))
    _assert("hello world" in out, f"raw text missing: {out!r}")
    _assert("[ERROR]" in out, f"level prefix missing: {out!r}")


@scenario("CRC mismatch: yields BadFrame with reason")
def _s(elf_path: Path):
    r = Renderer(db=None, use_color=False, hyperlinks=False, timestamps=False)
    out = _run_render(r, FrameParser(), bad_crc_frame(0x00000008))
    _assert("badframe" in out and "crc mismatch" in out,
            f"crc-fail diagnostic missing: {out!r}")


@scenario("passthrough: legacy ASCII flows through verbatim")
def _s(elf_path: Path):
    r = Renderer(db=None, use_color=False, hyperlinks=False, timestamps=False)
    out = _run_render(r, FrameParser(), b"BOOTROM v1.2 ready\n\x00")
    _assert("BOOTROM v1.2 ready" in out, f"passthrough text missing: {out!r}")


@scenario("passthrough: high-rate stream flushes proactively at MAX_FRAME_SIZE")
def _s(elf_path: Path):
    r = Renderer(db=None, use_color=False, hyperlinks=False, timestamps=False)
    parser = FrameParser()
    # 10 KB of ASCII without a single 0x00 — should auto-flush in chunks.
    out = io.StringIO()
    big = b"X" * 10000
    flushes = 0
    for item in parser.feed(big):
        flushes += 1
        r.emit(item, out)
    _assert(flushes >= 10000 // FrameParser.MAX_FRAME_SIZE,
            f"expected ≥ {10000 // FrameParser.MAX_FRAME_SIZE} flushes, got {flushes}")
    # Drain anything that didn't hit the threshold.
    for item in parser.flush():
        r.emit(item, out)
    _assert(out.getvalue().count("X") == 10000,
            f"byte count drift: {out.getvalue().count('X')} vs 10000")


@scenario("interleaved text + frame: cleanly separated by 0x00")
def _s(elf_path: Path):
    db = TokenDatabase(elf_path)
    r = Renderer(db, use_color=False, hyperlinks=False, timestamps=False)
    parser = FrameParser()
    wire = b"W3-Core> " + b"\x00" + compact_frame(0x00000008, [99])
    out = _run_render(r, parser, wire)
    _assert("W3-Core> " in out, f"prompt missing: {out!r}")
    _assert("Match: 99" in out, f"frame missing: {out!r}")


@scenario("build-id banner: matches ELF -> ✓ marker")
def _s(elf_path: Path):
    db = TokenDatabase(elf_path)
    r = Renderer(db, use_color=False, hyperlinks=False, timestamps=False)
    out = _run_render(r, FrameParser(), build_id_frame(db.build_id))
    _assert("matches ELF ✓" in out, f"match marker missing: {out!r}")


@scenario("build-id banner: mismatched ELF -> loud warning")
def _s(elf_path: Path):
    db = TokenDatabase(elf_path)
    r = Renderer(db, use_color=False, hyperlinks=False, timestamps=False)
    fake = bytes(reversed(db.build_id))
    out = _run_render(r, FrameParser(), build_id_frame(fake))
    _assert("ELF MISMATCH" in out, f"mismatch warning missing: {out!r}")


@scenario("auto-detect: cold start + matching banner attaches ELF transparently")
def _s(elf_path: Path):
    index = find_elfs_by_build_ids([Path("build/firmware")])
    db = TokenDatabase(elf_path)
    r = Renderer(db=None, use_color=False, hyperlinks=False, timestamps=False,
                 auto_detect_index=index, auto_detect_roots=[Path("build/firmware")])
    out = _run_render(r, FrameParser(), build_id_frame(db.build_id))
    _assert("auto-detected" in out, f"missing [auto-detected: …]: {out!r}")
    _assert("matches ELF ✓" in out, f"missing match marker: {out!r}")
    _assert(r.db is not None, "renderer.db not attached")


@scenario("auto-detect: rebuild + reflash mid-session triggers reload")
def _s(elf_path: Path):
    """Drop a fresh ELF into a fake build dir mid-session, send banner,
    expect a reindex + auto-detect."""
    fake_root = Path("/tmp/_log_selftest_build")
    shutil.rmtree(fake_root, ignore_errors=True)
    # find_elfs_by_build_ids only walks `firmware/build/firmware/` subtrees,
    # so mirror that shape here (treat fake_root as a worktree root).
    drop_dir = fake_root / "firmware" / "build" / "firmware"
    drop_dir.mkdir(parents=True)
    try:
        # Cold start with an empty index.
        r = Renderer(db=None, use_color=False, hyperlinks=False, timestamps=False,
                     auto_detect_index={}, auto_detect_roots=[fake_root])
        # Build-id banner with no match -> expect "no ELF" diagnostic.
        bid = TokenDatabase(elf_path).build_id
        out = _run_render(r, FrameParser(), build_id_frame(bid))
        _assert("no ELF loaded" in out, f"expected no-ELF warning, got {out!r}")

        # Drop the real ELF in and wait past the 1s reindex throttle.
        time.sleep(1.1)
        shutil.copyfile(elf_path, drop_dir / "fresh.signed.elf")

        # Fresh banner — pretend the device rebooted.
        r._build_id_seen = None
        out = _run_render(r, FrameParser(), build_id_frame(bid))
        _assert("reindexed" in out, f"expected [reindexed: …]: {out!r}")
        _assert("auto-detected" in out, f"expected [auto-detected: …]: {out!r}")
    finally:
        shutil.rmtree(fake_root, ignore_errors=True)


@scenario("auto-relabel: core ELF -> label 'core'")
def _s(elf_path: Path):
    if "w3-core" not in str(elf_path):
        return  # only meaningful for w3-core fixture
    db = TokenDatabase(elf_path)
    r = Renderer(db=None, use_color=False, hyperlinks=False, timestamps=False,
                 auto_detect_index={db.build_id: elf_path},
                 auto_detect_roots=[Path("build/firmware")],
                 label="usbserial-XYZ", auto_relabel=True)
    _run_render(r, FrameParser(), build_id_frame(db.build_id))
    _assert(r.label == "core", f"expected 'core', got {r.label!r}")


@scenario("auto-relabel: explicit user label kept (auto_relabel=False)")
def _s(elf_path: Path):
    if "w3-core" not in str(elf_path):
        return
    db = TokenDatabase(elf_path)
    r = Renderer(db=None, use_color=False, hyperlinks=False, timestamps=False,
                 auto_detect_index={db.build_id: elf_path},
                 auto_detect_roots=[Path("build/firmware")],
                 label="myCore", auto_relabel=False)
    _run_render(r, FrameParser(), build_id_frame(db.build_id))
    _assert(r.label == "myCore", f"expected 'myCore', got {r.label!r}")


@scenario("timestamps: prefix emitted on rendered frames")
def _s(elf_path: Path):
    db = TokenDatabase(elf_path)
    r = Renderer(db, use_color=False, hyperlinks=False, timestamps=True)
    out = _run_render(r, FrameParser(), compact_frame(0x00000008, [42]))
    _assert("[+" in out and "]" in out, f"timestamp prefix missing: {out!r}")


@scenario("--no-timestamps: prefix suppressed")
def _s(elf_path: Path):
    db = TokenDatabase(elf_path)
    r = Renderer(db, use_color=False, hyperlinks=False, timestamps=False)
    out = _run_render(r, FrameParser(), compact_frame(0x00000008, [42]))
    _assert(not out.startswith("[+"), f"unexpected timestamp prefix: {out!r}")


@scenario("hyperlinks: OSC 8 wraps file:line when source resolves")
def _s(elf_path: Path):
    db = TokenDatabase(elf_path)
    r = Renderer(db, use_color=False, hyperlinks=True, timestamps=False,
                 editor_uri="vscode://file/{path}:{line}")
    r.hyperlinks = True  # bypass isatty gate for test
    out = _run_render(r, FrameParser(), compact_frame(0x00000008, [42]))
    _assert("\x1b]8;;vscode://" in out, f"OSC 8 + vscode:// missing: {out!r}")
    _assert("fpc_cmd.c:69" in out, f"display text missing: {out!r}")


@scenario("--no-hyperlinks: plain file:line, no OSC 8")
def _s(elf_path: Path):
    db = TokenDatabase(elf_path)
    r = Renderer(db, use_color=False, hyperlinks=False, timestamps=False)
    out = _run_render(r, FrameParser(), compact_frame(0x00000008, [42]))
    _assert("\x1b]8;;" not in out, f"OSC 8 should be suppressed: {out!r}")


@scenario("editor_uri: malformed template falls back to file://")
def _s(elf_path: Path):
    db = TokenDatabase(elf_path)
    r = Renderer(db, use_color=False, hyperlinks=True, timestamps=False,
                 editor_uri="bad{nope}://{whatever}")
    r.hyperlinks = True
    out = _run_render(r, FrameParser(), compact_frame(0x00000008, [42]))
    _assert("file://" in out, f"expected fallback to file://: {out!r}")


@scenario("short_paths: only basename rendered")
def _s(elf_path: Path):
    db = TokenDatabase(elf_path)
    r = Renderer(db, use_color=False, hyperlinks=False, timestamps=False,
                 short_paths=True)
    out = _run_render(r, FrameParser(), compact_frame(0x00000008, [42]))
    _assert("(fpc_cmd.c:" in out, f"basename missing: {out!r}")
    _assert("../" not in out, f"path component leaked: {out!r}")


@scenario("--full-paths: full __FILE__ path rendered")
def _s(elf_path: Path):
    db = TokenDatabase(elf_path)
    r = Renderer(db, use_color=False, hyperlinks=False, timestamps=False,
                 short_paths=False)
    out = _run_render(r, FrameParser(), compact_frame(0x00000008, [42]))
    _assert("../" in out or "hal/biometrics/src/fpc_cmd.c" in out,
            f"full path expected: {out!r}")


@scenario("color: ANSI escapes wrap level prefix")
def _s(elf_path: Path):
    db = TokenDatabase(elf_path)
    r = Renderer(db, use_color=True, hyperlinks=False, timestamps=False)
    out = _run_render(r, FrameParser(), compact_frame(0x00000008, [42], level=3))
    _assert("\x1b[" in out, f"color escape missing: {out!r}")


@scenario("--no-color: no ANSI escapes")
def _s(elf_path: Path):
    db = TokenDatabase(elf_path)
    r = Renderer(db, use_color=False, hyperlinks=False, timestamps=False)
    out = _run_render(r, FrameParser(), compact_frame(0x00000008, [42], level=3))
    _assert("\x1b[" not in out, f"color escape should be absent: {out!r}")


@scenario("multi-port: per-port label prefix in output")
def _s(elf_path: Path):
    db = TokenDatabase(elf_path)
    r = Renderer(db, use_color=False, hyperlinks=False, timestamps=False,
                 label="core")
    out = _run_render(r, FrameParser(), compact_frame(0x00000008, [42]))
    _assert("[core]" in out, f"port label missing: {out!r}")


@scenario("token DB: build_id round-trip via cache")
def _s(elf_path: Path):
    _CACHE_PATH.unlink(missing_ok=True)
    db1 = TokenDatabase(elf_path)
    bid = db1.build_id
    _assert(bid is not None and len(bid) == BUILD_ID_LEN,
            f"build_id wrong: {bid}")

    # Fresh DB hits the disk; bytes should match.
    db2 = TokenDatabase(elf_path)
    _assert(db2.build_id == bid, "build_id mismatch across instances")

    # find_elfs_by_build_ids should put this ELF in the index keyed by bid.
    index = find_elfs_by_build_ids([Path("build/firmware")])
    _assert(bid in index, f"build_id {bid.hex()[:8]}… missing from index")
    _assert(index[bid] == elf_path or index[bid].resolve() == elf_path.resolve(),
            f"index points at wrong elf: {index[bid]} vs {elf_path}")


@scenario("port picker: index parsing accepts singletons, lists, ranges, 'all'")
def _s(elf_path: Path):
    """`_parse_port_selection` is the pure-function core of the interactive
    port menu. Anything ambiguous or out-of-range must return None so the
    caller re-prompts instead of silently misinterpreting input."""
    from tasks.log import _parse_port_selection as p
    _assert(p("1", 3) == [1], "single index")
    _assert(p("1,3", 3) == [1, 3], "comma list")
    _assert(p("1 3", 3) == [1, 3], "space list")
    _assert(p("1, 2, 3", 3) == [1, 2, 3], "comma+space")
    _assert(p("all", 3) == [1, 2, 3], "'all' selects everything")
    _assert(p("ALL", 3) == [1, 2, 3], "'all' is case-insensitive")
    _assert(p("1,1,2", 3) == [1, 2], "duplicates collapsed")
    _assert(p("", 3) == [], "empty input means 'use default'")
    _assert(p("4", 3) is None, "out-of-range index rejected")
    _assert(p("0", 3) is None, "zero rejected (1-based)")
    _assert(p("foo", 3) is None, "non-numeric rejected")
    _assert(p("1,foo", 3) is None, "partially-numeric rejected")


@scenario("compact frame: non-int log_id is downgraded to BadFrame, not a crash")
def _s(elf_path: Path):
    """Regression: a corrupted/malicious frame whose first CBOR element isn't
    an int used to crash the reader thread when _render_compact tried to
    `0x%08x`-format it. It must now degrade to BadFrame so the monitor keeps
    running."""
    r = Renderer(db=None, use_color=False, hyperlinks=False, timestamps=False)
    parser = FrameParser()
    # Build a frame whose CBOR payload is `["oops"]` — valid CBOR but log_id
    # is a string, not an int.
    payload = cbor2.dumps(["oops"])
    inner = bytes([MAGIC, TYPE_COMPACT, 1]) + payload
    crc = crc16_ccitt(inner)
    frame = CobsEncoder.encode(inner + bytes([crc & 0xFF, (crc >> 8) & 0xFF]))
    out = _run_render(r, parser, frame)
    _assert("badframe" in out and "log_id not an int" in out,
            f"expected BadFrame for non-int log_id, got: {out!r}")


@scenario("passthrough: chunk with embedded newline prefixes both sides")
def _s(elf_path: Path):
    """Regression: a single chunk like 'abc\\ndef' must produce a prefix
    before 'abc' AND before 'def' — the post-newline tail used to be
    rendered without a prefix."""
    r = Renderer(db=None, use_color=False, hyperlinks=False, timestamps=True)
    parser = FrameParser()
    out = io.StringIO()
    for item in parser.feed(b"abc\ndef\x00"):
        r.emit(item, out)
    rendered = out.getvalue()
    _assert(rendered.count("[+") == 2,
            f"expected 2 prefixes, got {rendered.count('[+')}: {rendered!r}")
    _assert("abc" in rendered and "def" in rendered,
            f"text dropped: {rendered!r}")


@scenario("CRC algorithm: pinned vector matches xmodem reference")
def _s(elf_path: Path):
    """Anchor the CRC implementation against a fixed test vector so a
    symmetric bug in both encoder and decoder can't silently pass."""
    from bitkey.log_decoder import crc16_ccitt
    # CRC-16/CCITT (xmodem) of "123456789" is 0x31C3.
    _assert(crc16_ccitt(b"123456789") == 0x31C3,
            f"CRC mismatch: got 0x{crc16_ccitt(b'123456789'):04x}, want 0x31C3")
    _assert(crc16_ccitt(b"") == 0x0000, "empty CRC must be init value 0x0000")


@scenario("compact frame: multi-byte UTF-8 string arg decodes")
def _s(elf_path: Path):
    """CBOR encodes UTF-8 text type for str args; verify the host decoder
    round-trips characters above ASCII range without mangling."""
    db = TokenDatabase(elf_path)
    r = Renderer(db, use_color=False, hyperlinks=False, timestamps=False)
    # Find a `%s` token in the loaded ELF; if there isn't one, skip.
    log_id = next((lid for lid, info in db.mapping.items() if "%s" in info.fmt), None)
    if log_id is None:
        return
    out = _run_render(r, FrameParser(), compact_frame(log_id, ["héllo→世界"]))
    _assert("héllo→世界" in out, f"UTF-8 string round-trip failed: {out!r}")


@scenario("build-id banner mid-stream: clean dispatch without polluting prior frame")
def _s(elf_path: Path):
    """A banner arriving while passthrough text is buffered should render the
    text first, then the banner — not interleave."""
    db = TokenDatabase(elf_path)
    r = Renderer(db, use_color=False, hyperlinks=False, timestamps=False)
    parser = FrameParser()
    # Stream: half a line of text, then 0x00 delimiter, then a build-id frame.
    wire = b"prompt> partial " + b"\x00" + build_id_frame(db.build_id)
    out = _run_render(r, parser, wire)
    _assert("prompt> partial" in out, f"text dropped: {out!r}")
    _assert("matches ELF ✓" in out, f"banner missing: {out!r}")
    # The text must appear before the banner in the rendered output.
    _assert(out.index("prompt> partial") < out.index("matches ELF"),
            f"banner rendered before text: {out!r}")


@scenario("frame parser: empty input produces nothing")
def _s(elf_path: Path):
    parser = FrameParser()
    items = list(parser.feed(b""))
    _assert(items == [], f"empty input produced items: {items}")
    items = list(parser.flush())
    _assert(items == [], f"empty flush produced items: {items}")


@scenario("frame parser: lone 0x00 doesn't yield anything")
def _s(elf_path: Path):
    parser = FrameParser()
    items = list(parser.feed(b"\x00\x00\x00"))
    _assert(items == [], f"unexpected items from lone delimiters: {items}")


@scenario("passthrough: one-byte-per-chunk echo gets a single prefix per line")
def _s(elf_path: Path):
    """Regression: after a frame renders, when the device echoes typed bytes
    one at a time, the timestamp prefix must only appear at the start of the
    typed line — not before every echoed byte."""
    db = TokenDatabase(elf_path)
    r = Renderer(db, use_color=False, hyperlinks=False, timestamps=True)
    parser = FrameParser()
    out = io.StringIO()
    # First render a frame so the post-frame state matches what triggers
    # the bug in real use.
    for item in parser.feed(compact_frame(0x00000008, [42])):
        r.emit(item, out)
    out.seek(0); out.truncate()  # ignore the frame's prefix; only count the typed line.
    # Mirror the real `_stream_serial` loop: feed each byte, then flush so
    # buffered passthrough emerges live.
    for ch in b"log_test\n":
        for item in parser.feed(bytes([ch])):
            r.emit(item, out)
        for item in parser.flush():
            r.emit(item, out)
    rendered = out.getvalue()
    # Exactly one timestamp prefix on the line.
    _assert(rendered.count("[+") == 1,
            f"expected 1 prefix, got {rendered.count('[+')}: {rendered!r}")
    _assert("log_test" in rendered, f"text dropped: {rendered!r}")


@scenario("idle flush: buffered text emerges when stream goes quiet")
def _s(elf_path: Path):
    r = Renderer(db=None, use_color=False, hyperlinks=False, timestamps=False)
    parser = FrameParser()
    out = io.StringIO()
    for item in parser.feed(b"partial text without newline"):
        r.emit(item, out)
    # Buffer not yet at MAX_FRAME_SIZE, no auto-flush. Idle drain:
    for item in parser.flush():
        r.emit(item, out)
    _assert("partial text without newline" in out.getvalue(),
            f"idle flush failed to emit: {out.getvalue()!r}")


# ─── runner ─────────────────────────────────────────────────────────────────

def run() -> int:
    """Run all scenarios; return process-style exit code (0 = pass)."""
    elf_path = _pick_fixture_elf()
    if elf_path is None:
        print("FAIL: no signed ELF available under build/firmware. "
              "Run `inv build.targets -p w3-core` first.")
        return 2

    print(f"# fixture ELF: {elf_path}")
    print(f"# {len(_SCENARIOS)} scenarios\n")

    failures = []
    for i, (name, fn) in enumerate(_SCENARIOS, 1):
        try:
            fn(elf_path)
            print(f"  [{i:2d}/{len(_SCENARIOS):2d}] PASS  {name}")
        except AssertionError as e:
            failures.append((name, str(e)))
            print(f"  [{i:2d}/{len(_SCENARIOS):2d}] FAIL  {name}\n        → {e}")
        except Exception as e:
            failures.append((name, f"{type(e).__name__}: {e}"))
            print(f"  [{i:2d}/{len(_SCENARIOS):2d}] ERR   {name}\n        → {type(e).__name__}: {e}")

    print()
    if failures:
        print(f"\n{len(failures)} of {len(_SCENARIOS)} scenarios failed.")
        return 1
    print(f"\nAll {len(_SCENARIOS)} scenarios passed.")
    return 0


def _pick_fixture_elf() -> Optional[Path]:
    """Find a w3-core dev ELF that's known to have token 0x00000008 = `Match: %d`
    (in fpc_cmd.c:69). Falls back to any built dev ELF.
    """
    candidates = [
        "build/firmware/w3-core/app/w3-core/application/w3a-core-pdvt-app-a-dev.signed.elf",
        "build/firmware/w3-core/app/w3-core/application/w3a-core-evt-app-a-dev.signed.elf",
    ]
    for c in candidates:
        p = Path(c)
        if p.exists():
            return p
    # Fallback: any signed dev ELF.
    for p in Path("build/firmware").rglob("*-app-a-dev.signed.elf"):
        if "mfgtest" not in p.name:
            return p
    return None


if __name__ == "__main__":
    raise SystemExit(run())
