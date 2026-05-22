"""Tokenized log decoder tasks (`inv log.monitor`, `inv log.dump-tokens`)."""

from __future__ import annotations

import os
import sys
from pathlib import Path

import click
from invoke import task

# fmt: off
sys.path.insert(0, str(Path(__file__).parent.parent))
from bitkey.meson import MesonBuild
from bitkey.log_decoder import (FrameParser, PortSpec, Renderer,
                                _stream_serial, _stream_stdin)
from bitkey.log_tokens import (TokenDatabase, find_elfs_by_build_id,
                               find_elfs_by_build_ids)
from tasks.lib.config import get_defaults
from tasks.lib.paths import BUILD_FW_DIR
# fmt: on


# Env var lets users park default search roots in their shell rc, so they
# don't have to pass --build-root every invocation when they juggle multiple
# worktrees. Accepts a `:`-separated list (PATH-style).
_BUILD_ROOT_ENV = "BITKEY_LOG_BUILD_ROOT"


def _discover_serial_ports():
    """Return the list of USB serial devices we'd consider monitoring.

    On macOS we drop `/dev/tty.*` (the matching `/dev/cu.*` is what we want;
    opening tty.* blocks waiting for DCD). We also filter to ports that have
    a USB VID so the menu doesn't list bluetooth headsets, system debug
    consoles, etc. — pyserial only populates `.vid` for real USB devices.
    """
    from serial.tools import list_ports
    is_mac = sys.platform == "darwin"
    out = []
    for p in list_ports.comports():
        if is_mac and p.device.startswith("/dev/tty."):
            continue
        if p.vid is None:
            continue
        out.append(p)
    return sorted(out, key=lambda p: p.device)


def _parse_port_selection(raw, n_ports):
    """Parse user input like '1', '1,3', '1 3', or 'all' into 1-based indices.

    Returns `None` on invalid input (caller re-prompts) or an empty list when
    nothing is selected. Pure function — easy to unit-test."""
    raw = raw.strip().lower()
    if not raw:
        return []
    if raw == "all":
        return list(range(1, n_ports + 1))
    picks = []
    for tok in raw.replace(",", " ").split():
        try:
            idx = int(tok)
        except ValueError:
            return None
        if not (1 <= idx <= n_ports):
            return None
        if idx not in picks:
            picks.append(idx)
    return picks


def _prompt_select_ports(ports, default=None):
    """Show a numbered menu of `ports` and let the user pick one or more.

    `default` is a device path; if it appears in the list, accepting the
    prompt with an empty line picks it. Multi-select via comma/space-separated
    indices, or `all`. Returns a list of device paths (possibly empty if the
    user aborts via Ctrl-D / Ctrl-C)."""
    default_idx = None
    if default:
        for i, p in enumerate(ports, 1):
            if p.device == default:
                default_idx = i
                break

    click.echo()
    click.echo(click.style("Available serial devices:", bold=True))
    for i, p in enumerate(ports, 1):
        desc = (p.description or "").strip()
        if desc in ("", "n/a"):
            desc = ""
        manufacturer = (p.manufacturer or "").strip()
        line = f"  {i}) {p.device}"
        if desc:
            line += f"  [{desc}]"
        if manufacturer and manufacturer not in desc:
            line += f"  ({manufacturer})"
        if i == default_idx:
            line += click.style("  (default)", fg="cyan")
        click.echo(line)
    click.echo()

    label = "Select port(s) — number, comma-separated list, or 'all'"
    if default_idx is not None:
        label += f" [{default_idx}]"
    while True:
        try:
            raw = click.prompt(label, default="", show_default=False)
        except (KeyboardInterrupt, EOFError):
            click.echo()
            return []
        if not raw.strip() and default_idx is not None:
            return [ports[default_idx - 1].device]
        indices = _parse_port_selection(raw, len(ports))
        if indices is None:
            click.echo(click.style("Invalid selection; try again.", fg="red"))
            continue
        if not indices:
            continue
        return [ports[i - 1].device for i in indices]


def _resolve_build_roots(build_root):
    """Pick the directories we'll scan for `*.signed.elf`.

    Precedence: explicit --build-root (one or more) > $BITKEY_LOG_BUILD_ROOT
    (`:`-separated list) > BUILD_FW_DIR. Always returns a list.
    """
    if build_root:
        # invoke iterable=[] gives us a list of strings; normalise.
        roots = list(build_root) if not isinstance(
            build_root, str) else [build_root]
        return [Path(r).expanduser().resolve() for r in roots if r]
    env = os.environ.get(_BUILD_ROOT_ENV)
    if env:
        return [Path(p).expanduser().resolve() for p in env.split(os.pathsep) if p]
    return [BUILD_FW_DIR]


def _resolve_elf(c, elf, platform=None, target=None):
    """If --elf wasn't given, pick the default platform/target's signed ELF.

    When `platform` is overridden but `target` is not, look up the platform's
    default target from invoke.json so we don't try to mix (e.g.) a w3-uxc
    platform with the w1 default target.
    """
    if elf:
        elf_path = Path(elf).expanduser().resolve()
        if not elf_path.exists():
            click.echo(click.style(f"ELF not found: {elf_path}", fg="red"))
            return None
        return elf_path

    if platform and not target:
        defaults = get_defaults()
        if platform in defaults:
            target = defaults[platform].get("target")

    mb = MesonBuild(c, platform=platform, target=target)
    candidate = mb.target_path(mb.target.elf)
    if not candidate or not candidate.exists():
        click.echo(click.style(
            f"Could not locate a default ELF for platform={platform or c.platform} "
            f"target={target or c.target}; pass --elf path/to/firmware.elf "
            "(or build first).", fg="red"))
        return None
    return candidate


@task(iterable=["build_root", "port"], help={
    "port": "Serial port (default: invoke.json monitor_port). Repeat to monitor "
            "multiple devices simultaneously, e.g. CORE + UXC at the same time. "
            "Optional `label=path` syntax (e.g. --port core=/dev/cu.usbserial-X) "
            "for a friendlier per-line prefix; defaults to the device basename suffix.",
    "elf": "Firmware ELF for token lookup. Auto-detected from build-id banner if omitted.",
    "baud": "Serial baud rate (default 115200).",
    "tee": "Also write the raw byte stream to this file (single port) or directory "
           "(multi-port: one `<label>.bin` per port).",
    "log_file": "Path for the rendered text capture (ANSI-stripped). "
                "Defaults to ./monitor.log; pass empty or `--no-log` to disable.",
    "no_log": "Disable the default monitor.log capture.",
    "platform": "Override platform when resolving an ELF (e.g. w3-uxc).",
    "target": "Override target when resolving an ELF.",
    "no_tokens": "Skip ELF token lookup; show frames as raw token+args.",
    "no_color": "Disable ANSI color output.",
    "read_only": "Don't forward stdin keystrokes to the serial port.",
    "no_elf_reload": "Don't auto-reload the ELF on rebuild (default: on boot if mtime advanced).",
    "no_auto_detect": "Don't auto-detect/suggest ELFs from the build-id banner.",
    "build_root": "Directory to scan for signed ELFs during auto-detect. "
                  "Repeat for multiple roots (--build-root A --build-root B). "
                  "Defaults to $BITKEY_LOG_BUILD_ROOT (`:`-separated list) or this "
                  "firmware's build dir. Point at worktree-parent dirs (e.g. "
                  "~/conductor/workspaces/wallet) to keep one monitor session "
                  "running across multiple worktrees scattered around the disk.",
    "full_paths": "Show the full source path embedded by __FILE__ (default: just the basename).",
    "no_timestamps": "Don't prefix each line with a monotonic timestamp.",
    "no_hyperlinks": "Don't render OSC 8 hyperlinks for `(file:line)`.",
    "editor_uri": "URI template for OSC 8 hyperlinks. Placeholders: `{path}`, "
                  "`{line}`. Defaults to $BITKEY_LOG_EDITOR_URI or "
                  "`file://{path}#L{line}`. Try `vscode://file/{path}:{line}` "
                  "for VS Code/Cursor, `cursor://file/{path}:{line}` for "
                  "Cursor specifically.",
})
def monitor(c, port=None, elf=None, baud=115200, tee=None,
            log_file="monitor.log", no_log=False,
            platform=None, target=None, no_tokens=False, no_color=False,
            read_only=False, no_elf_reload=False, no_auto_detect=False,
            build_root=None, full_paths=False,
            no_timestamps=False, no_hyperlinks=False, editor_uri=None):
    """Stream tokenized log frames from a UART, decoding against an ELF.

    Bidirectional by default: stdin keystrokes are forwarded to the device so
    the firmware shell stays interactive. Press Ctrl-C to exit.

    ELF resolution: if --elf or --platform is given, that ELF is loaded up
    front. Otherwise the decoder runs in auto-detect mode: every signed ELF
    under build/firmware/ is indexed by its build ID, and the right one is
    attached when the device emits its build-id banner at boot.

    Beyond that, on every device reboot the ELF's mtime is checked; if you
    rebuilt and reflashed since the last load, the token table is reloaded
    so log lines stay in sync with the live firmware.

    Tweaks: --no-elf-reload (pin original ELF), --no-auto-detect (don't
    scan or suggest), --no-tokens (don't decode at all).
    """
    # Normalize --port into a list of (label, path) pairs. Accepts the
    # `label=path` form for friendlier multi-port output; otherwise derives
    # a label from the device basename suffix (e.g. `BG01D939`).
    raw_ports = list(port) if port else []
    if not raw_ports:
        fallback = c.monitor_port if hasattr(c, "monitor_port") else None
        if fallback:
            raw_ports = [fallback]
    if not raw_ports:
        # Nothing specified anywhere — discover USB serial devices and let the
        # user pick interactively. Multi-select supported so a CORE+UXC pair
        # is one prompt away from a full session (auto-relabel will tag the
        # output once each device's build-id banner identifies it).
        available = _discover_serial_ports()
        if not available:
            click.echo(click.style(
                "No serial ports detected. Plug in a device, or pass --port "
                "explicitly, or set monitor_port in invoke.json.", fg="red"))
            sys.exit(1)
        raw_ports = _prompt_select_ports(available)
        if not raw_ports:
            click.echo("No port selected; exiting.")
            sys.exit(1)

    port_pairs = []
    for raw in raw_ports:
        if "=" in raw and not raw.startswith("/"):
            lbl, _, path = raw.partition("=")
            user_explicit = True
        else:
            path = raw
            lbl = Path(raw).name.replace("tty.usbserial-",
                                         "").replace("cu.usbserial-", "")
            user_explicit = False
        port_pairs.append((lbl, path, user_explicit))
    multi = len(port_pairs) > 1

    db = None
    elf_path = None
    explicit_elf = elf is not None or platform is not None or target is not None
    if not no_tokens and explicit_elf:
        elf_path = _resolve_elf(c, elf, platform=platform, target=target)
        if elf_path is None:
            sys.exit(1)
        db = TokenDatabase(elf_path)
        click.echo(click.style(
            f"Loaded {len(db)} tokens from {elf_path.name}", fg="cyan"))

    auto_detect_index = {}
    auto_detect_roots = []
    if not no_tokens and not no_auto_detect:
        auto_detect_roots = _resolve_build_roots(build_root)
        auto_detect_index = find_elfs_by_build_ids(auto_detect_roots)
        roots_str = ", ".join(str(r) for r in auto_detect_roots)
        if auto_detect_index:
            click.echo(click.style(
                f"Indexed {len(auto_detect_index)} signed ELFs under {roots_str} "
                "for build-id auto-detect.", fg="cyan"))
        elif not explicit_elf:
            click.echo(click.style(
                f"No signed ELFs found under {roots_str}; auto-detect won't help.",
                fg="yellow"))

    # Resolve --tee. For single port it's a regular file; for multi it must
    # be a directory and each port writes <dir>/<label>.bin inside.
    tee_root = Path(tee).expanduser().resolve() if tee else None
    if multi and tee_root is not None:
        tee_root.mkdir(parents=True, exist_ok=True)

    use_color = not no_color and sys.stdout.isatty()
    show_label = multi  # only prefix lines with [label] when there's >1 port
    resolved_editor_uri = editor_uri or os.environ.get("BITKEY_LOG_EDITOR_URI")
    specs = []
    for lbl, path, user_explicit in port_pairs:
        # Each port gets its own parser + renderer so per-port state (e.g.
        # last-seen build_id, ELF reload mtime) doesn't cross-pollute. They
        # share the auto-detect index but each holds its own copy of roots.
        renderer = Renderer(
            # Each renderer holds the same db reference initially; auto-detect
            # may diverge them later (e.g. CORE attaches w3-core ELF, UXC
            # attaches w3-uxc).
            db=TokenDatabase(elf_path) if elf_path else None,
            use_color=use_color,
            auto_reload=not no_elf_reload,
            auto_detect_index=auto_detect_index,
            auto_detect_roots=auto_detect_roots,
            short_paths=not full_paths,
            timestamps=not no_timestamps,
            hyperlinks=not no_hyperlinks,
            editor_uri=resolved_editor_uri,
            label=lbl if show_label else None,
            # Only auto-rewrite the label when the user didn't explicitly
            # name this port via `name=path`. If they did, keep their name.
            auto_relabel=show_label and not user_explicit,
        )
        if tee_root is None:
            spec_tee = None
        elif multi:
            spec_tee = tee_root / f"{lbl}.bin"
        else:
            spec_tee = tee_root
        specs.append(PortSpec(label=lbl, path=path, baud=baud,
                              parser=FrameParser(),
                              renderer=renderer,
                              tee_path=spec_tee))

    # Default-on rendered-text capture (matches the legacy `inv monitor` UX).
    # Empty string or --no-log disables it.
    log_path = None if no_log or not log_file else Path(log_file).expanduser()
    _stream_serial(specs, read_only=read_only, log_file=log_path)


@task(help={
    "elf": "Firmware ELF (defaults to the active target's signed ELF).",
    "platform": "Override platform when resolving the default ELF.",
    "target": "Override target when resolving the default ELF.",
    "limit": "Max number of entries to print (0 = all).",
})
def dump_tokens(c, elf=None, platform=None, target=None, limit=0):
    """Print the token table extracted from the ELF's `log_fmt` section."""
    elf_path = _resolve_elf(c, elf, platform=platform, target=target)
    if elf_path is None:
        sys.exit(1)

    db = TokenDatabase(elf_path)
    items = sorted(db.mapping.items())
    click.echo(f"# {len(items)} tokens in {elf_path}")
    for i, (log_id, info) in enumerate(items):
        if limit and i >= limit:
            click.echo(f"# ...{len(items) - i} more truncated")
            break
        click.echo(f"0x{log_id:08x}  {info.filename}:{info.line}  "
                   f"n_args={info.n_args}  fmt={info.fmt!r}")


@task
def selftest(c):
    """Run synthetic decoder scenarios (frames, passthrough, auto-detect, …).

    Builds frames in-memory and feeds them through `FrameParser` + `Renderer`
    so we can validate parameter combos and corner cases without a device.
    Picks a w3-core dev ELF as the fixture; build that target first if none
    is found.
    """
    from bitkey import log_selftest
    sys.exit(log_selftest.run())


@task(help={
    "elf": "Firmware ELF (defaults to the active target's signed ELF).",
    "platform": "Override platform when resolving the default ELF.",
    "target": "Override target when resolving the default ELF.",
    "no_color": "Disable ANSI color output.",
})
def decode_stdin(c, elf=None, platform=None, target=None, no_color=False):
    """Decode tokenized log bytes piped on stdin (e.g. from a saved capture)."""
    db = None
    elf_path = _resolve_elf(c, elf, platform=platform, target=target)
    if elf_path is not None:
        db = TokenDatabase(elf_path)

    parser = FrameParser()
    renderer = Renderer(db, use_color=(not no_color and sys.stdout.isatty()))
    _stream_stdin(parser, renderer)
