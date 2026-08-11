# core-sim

WCA transport server for POSIX builds (firmware simulator). Emulates the EFR32 (w3-core) chip
for testing firmware flows without hardware.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Real Hardware                            │
├─────────────────────────────────────────────────────────────────┤
│  App (Phone)                                                    │
│      │ NFC                                                      │
│      ▼                                                          │
│  EFR32 (w3-core)              UART (UC)           UXC (w3-uxc)  │
│  - WCA protocol handler  ◄──────────────────────► - LVGL UI     │
│  - display_controller                             - Touch input │
│  - confirmation_handler                           - Renders     │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                         Emulator                                │
├─────────────────────────────────────────────────────────────────┤
│  App (Android emulator)                                         │
│      │ stdin/stdout                                             │
│      ▼                                                          │
│  core-sim             TCP socket              ui-simulate   │
│  - WCA protocol handler  ◄──────────────────────► - LVGL UI     │
│  - display_controller                             - SDL input   │
│  - confirmation_handler                           - Renders     │
└─────────────────────────────────────────────────────────────────┘
```

**Key insight:** core-sim owns the `display_controller` state machine (like EFR32),
while ui-simulate is a dumb renderer (like UXC). In connect mode, ui-simulate forwards
user actions to core-sim and renders whatever core-sim sends back.

## Maintenance contract

The single rule that keeps this simulator from rotting: **adding a firmware
feature must never require editing a parallel copy of firmware code inside
core-sim.** Concretely:

- **Task ports are the real W1 hardware ports.** core-sim compiles
  `app/tasks/{key_manager,fwup,sysinfo}/src/w1*/..._port.c` directly (see
  meson.build). When a port interface grows, update the W1 port on main —
  core-sim picks it up for free. Do not copy port code into `src/posix/`.
  The files in `src/posix/` are *platform* shims (crypto, bio, flash, log),
  not task logic.
- **Platform headers are passthroughs.** Every header in
  `lib/platform/inc/` is a one-line `#include` of the canonical header,
  with POSIX function implementations in `src/posix/stubs.c` /
  `task_stubs.c`. The one deliberate exception is `metadata.h` (placeholder
  values; the header explains why it cannot be a passthrough).
- **Generated code is generated.** `src/generated/stdio_auth_check.h` is
  rebuilt from the IPC YAML files on every `inv build.core-sim`; never edit
  or commit it.
- **RTOS semantics** are documented in `lib/rtos/src/posix/README.md`
  (pthread shim: host-preemptive, priorities advisory, timers poll-only)
  and pinned by `lib/rtos/src/posix/rtos_posix_test.c`.

## Simulation fidelity

| Area | Status | Where |
|------|--------|-------|
| Task logic (auth, key_manager, fwup, sysinfo) | **Real firmware code** (core impls + W1 hardware ports) | `app/tasks/*` |
| WCA protocol, IPC routing, wallet, policy, grant, onboarding, unlock, confirmation manager | **Real firmware code** | `lib/*` via meson deps |
| Crypto / secure engine | Real POSIX crypto layer (OpenSSL/secp256k1-backed) | `lib/crypto/src/posix/` |
| Storage | Real LittleFS on a RAM block device, persisted to `CORE_SIM_DATA_DIR` | `src/wallet_emulator.c`, `src/sim_persistence.c` |
| Core MCU FWUP (A/B slots, signatures) | Simulated flash: RAM-backed slots | `src/posix/fwup_addr.c` |
| UXC coprocessor FWUP / metadata / coredumps | **Not simulated** — W1 semantics (fwup asserts, sysinfo replies ERROR). To add: simulate a UXC peer behind the UC transport (where ui-simulate sits) and compile the real w3-core ports. | — |
| Biometrics | Simulated sensor (host condvars, scripted via emulator commands) | `src/posix/bio_sim.c` |
| Display | Real display_controller; rendering delegated to ui-simulate over TCP | `src/uxc_transport.c` |
| Secure channel | W1 variant (empty cert table — cert init is a no-op) | meson.build |
| Power, watchdog, telemetry, GPIO, metadata | No-op / placeholder stubs | `src/posix/task_stubs.c`, `lib/platform/inc/metadata.h` |
| Device identity / attestation | Generated dev identity (`CORE_SIM_PROVISION=1`) | `src/sim_provisioning.c` |

## Usage

### With ui-simulate (full UI)

**Option 1: Using the launcher daemon**
```bash
# Build and start the launcher daemon (Rust)
inv build-launcher
inv launcher  # Foreground mode

# Or use --ensure for background mode (used by Gradle)
inv launcher --ensure

# Then trigger start
echo "start" | nc localhost 5001
```

**Option 2: Manual startup**

Terminal 1 - Start core-sim with UI port:
```bash
./build/core-sim/app/core-sim/core-sim-w3 --ui-port 9000
```

Terminal 2 - Connect ui-simulate:
```bash
./build/core-sim/ui-simulate/ui-simulate --connect 127.0.0.1:9000
```

### Standalone (no UI)

```bash
./build/core-sim/app/core-sim/core-sim-w1
```

## Wire Protocol

### stdin/stdout (App ↔ core-sim)

```
[1-byte type][4-byte BE length][payload]

Types:
  0x00 = WCA APDU (NFC commands)
  0x01 = UI command
```

### TCP socket (core-sim ↔ ui-simulate)

Same framing, with UI subtypes:
```
Type 0x01, Subtype 0x10 = uxc_msg_host  (core-sim → ui-simulate)
Type 0x01, Subtype 0x11 = uxc_msg_device (ui-simulate → core-sim)
```

Subtypes are the first byte of the payload. The rest is protobuf-encoded
`fwpb_uxc_msg_host` or `fwpb_uxc_msg_device`.

## Message Flow Example (Wipe Device)

```
1. App ──wipe_state_cmd──► core-sim (stdin)
2. core-sim: confirmation_handler generates handles, triggers UI
3. core-sim: display_controller ──display_cmd──► ui-simulate (socket)
4. ui-simulate: renders "Wipe Device?" confirmation screen
5. User clicks Approve
6. ui-simulate ──display_action(APPROVE)──► core-sim (socket)
7. core-sim: display_controller transitions, confirmation_handler records result
8. core-sim: display_controller ──display_cmd──► ui-simulate (success screen)
9. App ──get_confirmation_result_cmd──► core-sim → SUCCESS
```

## Command Line Options

Two binaries are built: `core-sim-w1` (screenless, auto-confirms) and
`core-sim-w3` (W3 confirmation flow). Both accept:

| Option | Description |
|--------|-------------|
| `--ui-port PORT` | TCP port for ui-simulate connection |

| Environment variable | Description |
|-----------------------|-------------|
| `CORE_SIM_DATA_DIR` | Where persistent state lives (default: `$HOME/.core-sim`) |
| `CORE_SIM_RESET_STORAGE=1` | Wipe persisted state at startup |
| `CORE_SIM_PROVISION=1` | Generate a device identity for attestation testing |

## Building

```bash
cd firmware
inv build.core-sim            # core-sim-w1 + core-sim-w3
inv build.core-sim --with-ui  # also builds the ui-simulate renderer
```

`src/generated/stdio_auth_check.h` is regenerated from the IPC YAML
definitions on every `inv build.core-sim` run.

Builds on macOS (arm64) and Linux (x86_64, clang). On Linux the build
uses clang and unsets the hermit `GCC_EXEC_PREFIX` automatically (see
`python/bitkey/meson.py`); Criterion comes from `inv install.test-deps`.
On macOS, `brew install criterion openssl pkg-config` once.

## Testing

```bash
inv test                          # full host unit-test suite (includes
                                  # "rtos posix" and sim_provisioning)
bash app/core-sim/test_version.sh # boot + WCA version handshake smoke test
```

`test_version.sh` boots the binary against a throwaway `CORE_SIM_DATA_DIR`
and checks the typed-frame response; point it at the other binary with
`CORE_SIM_BIN=.../core-sim-w3`.

## Source Files

| File | Purpose |
|------|---------|
| `src/main.c` | Main loop with stdin/socket multiplexing |
| `src/posix/wca_glue.c` | WCA protocol framing and typed message I/O |
| `src/uxc_socket_server.c` | TCP server for ui-simulate |
| `src/uxc_transport.c` | UXC message routing (mirrors `uc_route`) |
| `src/handler_*.c` | Emulator/device control command handlers |
| `src/device_state.c` | Persistent emulator state (onboarding, auth mode) |
| `src/wallet_emulator.c` | Real wallet + LittleFS-backed filesystem |
| `src/posix/*` | POSIX platform stubs and simulated peripherals |
