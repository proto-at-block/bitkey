# lib/log

Console logging for the firmware. Provides `LOGI/LOGD/LOGW/LOGE` and an
`ASSERT_LOG` helper. Backed by Memfault Compact Logs in the RAM ring (so logs
ride along with cloud uploads regardless of the UART path) plus one of two UART
transports.

## UART transports

The on-wire format is selected at compile time via the `log_tokenized` meson
option (default `true`).

### `log_tokenized=false`

Each log line is `printf`-formatted on the device and emitted as ASCII over
UART. Same behavior the firmware shipped with before tokenization landed. No
tooling change required to read the stream.

### `log_tokenized=true` (default)

The format string is replaced with a 32-bit token at compile time and only the
token + binary-encoded args go over the wire. The format string lives at
virtual address `0xF0000000` in the `log_fmt` ELF section (already declared in
`config/partitions/*/*.jinja.ld`), which is **not loaded onto the device** —
that is where the flash savings come from.

Frame format (decoded, before COBS):

```
+--------+--------+--------+----------------------+--------------+
| magic  | type   | level  | payload (variable)   | crc16-ccitt  |
| 0xBF   | 1B     | 1B     | 0..N B               | LE, 2B       |
+--------+--------+--------+----------------------+--------------+
                  ^ COBS-encoded, terminated by 0x00
```

| Type   | Meaning                                                                |
|--------|------------------------------------------------------------------------|
| `0x01` | Compact log. Payload is a CBOR array `[log_id, args...]` produced by   |
|        | Memfault's compact log serializer; decode with `mflt-compact-log`      |
|        | (PyPI) against the firmware ELF.                                       |
| `0x02` | Raw text. Payload is UTF-8 bytes (no NUL). Used by `LOG*_RAW` /        |
|        | `LOG_FORCE_RAW` and panic/early-boot/mfgtest paths.                    |
| `0x10` | Build-ID banner. 20-byte payload = the firmware's Memfault-derived     |
|        | build ID (`g_memfault_sdk_derived_build_id`). Emitted once at boot     |
|        | so the host decoder can verify the supplied ELF matches the device.   |

`level` maps directly to `eMemfaultPlatformLogLevel` (`Debug=0 Info=1 Warning=2 Error=3`).

CRC16-CCITT (xmodem variant, init `0x0000`) covers `magic | type | level |
payload`. Frames are COBS-encoded (nanocobs, in-place) and terminated by a
`0x00` delimiter.

Raw text from non-tokenized callers (boot ROM, panic, legacy `printf`)
coexists on the same UART: anything between `0x00` delimiters whose first
decoded byte isn't `0xBF` is printed verbatim by the host decoder.

## Per-call / per-module raw escape hatches

Use these where a missing token DB would lose information (panic handlers,
bootloader, manufacturing test, code that runs before the RTOS scheduler):

```c
// Per call:
LOGI_RAW("plain text always, even when log_tokenized=true");
LOGE_RAW("crashed at pc=0x%08x", pc);

// Whole translation unit:
#define LOG_FORCE_RAW 1
#include "log.h"
```

`LOG*_RAW` and `LOG_FORCE_RAW` honor `disable_printf` (no output in that
build).

## Decoding tokenized logs

```bash
# Live decode (auto-detects the ELF from the device's build-id banner):
inv monitor --port /dev/cu.usbserial-…

# Or pin a specific ELF / platform:
inv monitor --port /dev/cu.usbserial-… --elf build/.../firmware.elf
inv monitor --port /dev/cu.usbserial-… --platform w3-core

# No --port and no monitor_port in invoke.json: pick from a menu of detected
# USB serial devices. Multi-select via comma-separated indices or `all`:
inv monitor

# Watch CORE + UXC at the same time (read-only, port label per line):
inv monitor \
  --port core=/dev/cu.usbserial-BG01D939 \
  --port uxc=/dev/cu.usbserial-BG031B9C

# Print every token entry from an ELF:
inv log.dump-tokens --elf build/.../firmware.elf

# Decode a saved capture (raw bytes on stdin):
cat capture.bin | inv log.decode-stdin --elf build/.../firmware.elf
```

Each rendered line is prefixed with a monotonic timestamp (`[+12.345]`) and,
when monitoring multiple ports, a port label (`[core]`). The `(file:line)`
in compact-log output is wrapped in an OSC 8 hyperlink to a `file://` URI of
the resolved source — clickable in iTerm2, Ghostty, Kitty, and the VS Code
terminal. Disable any of these with `--no-timestamps`, `--no-hyperlinks`,
or just observe the plain output in capture files (hyperlinks auto-disable
when stdout isn't a tty).

By default the rendered session is also tee'd to `monitor.log` in the
working directory (ANSI escapes stripped, so `cat` / `grep` produce
clean output). Pass `--log-file path/to/file` to redirect, or
`--no-log` to skip the capture entirely.

**Auto-detect**: with no `--elf`/`--platform`, the decoder indexes every
`*.signed.elf` under `build/firmware/` by its content-derived build ID and
attaches the matching one as soon as the device emits its build-id banner
at boot. If a banner doesn't match anything in the index (e.g. you just
finished a fresh build of a different variant), the index is rescanned in
place (throttled to once per second) so the new ELF gets picked up without
restarting the monitor. Pass `--no-auto-detect` to disable.

**Auto-reload**: the loaded ELF's mtime is checked at every device boot
(banner). If you rebuilt and reflashed since the last load, the token
table is reloaded so log lines stay in sync with the live firmware. Pass
`--no-elf-reload` to pin the originally loaded ELF.

So the normal dev loop — leave `inv monitor --port …` running, rebuild
+ reflash in another shell — Just Works: the new firmware boots, banner
arrives, fresh tokens load, log lines stay readable, no need to Ctrl-C and
relaunch.

Frames whose tokens aren't found in the ELF render as
`[LEVEL] (??) token=0x… args=[…]`; legacy ASCII text on the same UART
(boot ROM, panic, `LOG*_RAW`) passes through verbatim.

Token decoding is delegated to
[`mflt-compact-log`](https://pypi.org/project/mflt-compact-log/) (pinned in
`firmware/requirements.txt`). Memfault cloud uploads continue to work
unchanged — the ELF symbol upload flow already populates the compact-log
mapping there.

## Failure modes

| Situation                                | What the host sees                       |
|------------------------------------------|------------------------------------------|
| ELF matches firmware                     | Banner: `[build-id] device=… matches ELF ✓` then formatted log lines. |
| ELF mismatch (wrong build, valid token)  | Banner: `⚠ ELF MISMATCH ⚠ device=… elf=…; tokenized lines below WILL DECODE WRONG.` Loud red banner so you replace `--elf` immediately. |
| ELF unsigned (build-id symbol absent)    | Banner: `device=… elf=…: no build-id symbol (unsigned ELF? token decode may still work)`. |
| No `--elf` passed                        | Banner: `device=… (no ELF loaded for verification)`. |
| Wrong ELF, token offset out of range     | `??token=0x… type=0x01 args=[…]` plus raw hex dump of args. |
| CRC mismatch                             | `[log frame crc fail, len=N]`; decoder resyncs at next `0x00`. |
| Mixed binary + raw bytes on the wire     | Both render in order.                    |
| `LOG_FORCE_RAW` translation unit         | Plain text, no token DB lookup needed.   |

## Constraints

- Compact Logs are not supported in C++/host (Memfault SDK limitation), so
  fuzz and host test builds always use the formatted ASCII path.
- `MEMFAULT_LOG_MAX_LINE_SAVE_LEN` (default 80 B) caps the formatted size of
  string args saved into the compact log ring; arguments longer than that are
  truncated.
- Log calls are synchronous (run in caller context inside
  `RTOS_THREAD_WITH_PRIVILEGE`). They block briefly while encoding and writing
  to UART. Tokenized frames are roughly half the wire bytes of the equivalent
  ASCII line, so blocking time is shorter than the legacy path.
