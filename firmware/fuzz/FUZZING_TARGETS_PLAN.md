# Firmware Fuzzing Targets Plan

Security-focused fuzz harnesses targeting findings from the third-party security
engagement (BCW prefix). Each harness is an in-process libFuzzer target that
integrates with ClusterFuzz and can be run locally.

---

## Quick-start

```bash
# From firmware/
source activate          # hermit + clangd
inv fuzz                 # build all fuzz targets → build/host/

# Run a specific target (replace <target> with the name below)
./build/host/<target> -max_total_time=60 fuzz/fuzzer_inputs/<target>/
```

For macOS, LLVM's fuzzer runtime is not bundled with Apple's clang — use brew:

```bash
# Apple Silicon
CC=/opt/homebrew/opt/llvm@14/bin/clang \
CXX=/opt/homebrew/opt/llvm@14/bin/clang++ \
inv clean fuzz
```

---

## Target Map

### `wca-session-fuzz` — BCW-01, BCW-04, BCW-06, BCW-09, BCW-10

| Finding | Description |
|---------|-------------|
| BCW-01  | `cmd[4]` OOB read: APDU shorter than 5 bytes passed to `wca_handle_apdu()` |
| BCW-04  | Response-buffer overflow: `handle_proto_response` writes beyond allocated size |
| BCW-06  | Missing APDU-length check before reading `cmd[4]` (Lc byte) |
| BCW-09  | Stale-state replay: `PROTO_CONT` with Lc=0 processes previous command buffer |
| BCW-10  | Undrained response bytes reused across sessions without flushing |

**Source:** `firmware/lib/wca/src/wca_session_fuzz.cc`
**Meson:** `firmware/lib/wca/meson.build`
**Seeds:** `fuzz/fuzzer_inputs/wca-session-fuzz/` (5 seeds)
**Dict:** `fuzz/fuzzer_inputs/wca-session-fuzz/wca.dict`

**Input format** (`FuzzedDataProvider` stream):

```
[action : uint32_le]  [cmd_len : uint32_le]  [cmd_bytes : cmd_len bytes]
```

Actions: `kVersion=0`, `kProto=1`, `kResponseBufferExact=2`,
`kProtoContZeroLen=3`, `kGetResponse=4`, `kSessionReset=5`.

**Run locally:**

```bash
./build/host/wca-session-fuzz \
  -dict=fuzz/fuzzer_inputs/wca-session-fuzz/wca.dict \
  fuzz/fuzzer_inputs/wca-session-fuzz/
```

---

### `wca-proto-handlers-fuzz` — BCW-07, BCW-19, BCW-31

| Finding | Description |
|---------|-------------|
| BCW-07  | NULL deref: `proto_get_cmd()` returns NULL (empty input) → handler dereferences |
| BCW-19  | `fingerprint_reset_finalize`: arbitrary bytes in `grant.bytes` reach grant handler |
| BCW-31  | `handle_seal_csek`: `ASSERT(sizeof(unsealed_csek.bytes) == unsealed_csek.size)` fires on size mismatch |

**Source:** `firmware/lib/wca/src/wca_proto_handlers_fuzz.cc`
**Meson:** `firmware/lib/wca/meson.build`
**Seeds:** `fuzz/fuzzer_inputs/wca-proto-handlers-fuzz/` (2 seeds)
**Dict:** `fuzz/fuzzer_inputs/wca-proto-handlers-fuzz/proto.dict`

**Input format:**

```
[handler_idx : uint32_le]  [proto_len : uint32_le]  [proto_bytes : proto_len bytes]
```

Handler indices (0–6): `delete_fingerprint`, `list_fingerprints`,
`get_fingerprint_enrollment_status`, `cancel_fingerprint_enrollment`,
`start_fingerprint_enrollment`, `seal_csek`, `fingerprint_reset_finalize`.

**Run locally:**

```bash
./build/host/wca-proto-handlers-fuzz \
  -dict=fuzz/fuzzer_inputs/wca-proto-handlers-fuzz/proto.dict \
  fuzz/fuzzer_inputs/wca-proto-handlers-fuzz/
```

---

### `secure-channel-fuzz` — BCW-08, BCW-15, BCW-16

| Finding | Description |
|---------|-------------|
| BCW-08  | Decrypt: no length check before `memcpy` into fixed-size payload buffer |
| BCW-15  | Establish: plaintext length field not validated against actual ciphertext length |
| BCW-16  | Establish: missing bounds check on certificate chain length field |

**Source:** `firmware/lib/secure-channel/src/secure_channel_fuzz.cc`
**Meson:** `firmware/lib/secure-channel/meson.build` (posix/darwin/linux only)
**Seeds:** none yet — libFuzzer generates from scratch
**Dict:** none

**Input format:**

```
[op : uint8]  [rest…]
  op=0 → secure_channel_establish(data, size)
  op=1 → secure_channel_decrypt(data, size)
```

**Run locally:**

```bash
./build/host/secure-channel-fuzz
```

---

### `fwup-delta-fuzz` — BCW-25, BCW-26, BCW-36

| Finding | Description |
|---------|-------------|
| BCW-25  | Transfer: `sequence_id * max_chunk_size + offset` uint32 overflow before bounds check |
| BCW-26  | Delta transfer: `from_read()`/`from_seek()`/`to_write()` callbacks advance pointers without bounds |
| BCW-36  | Finish: state machine not validated before writing final block |

**Source:** `firmware/lib/fwup/fwup_delta_fuzz.cc`
**Meson:** `firmware/lib/fwup/meson.build`
**Seeds:** `fuzz/fuzzer_inputs/fwup-delta-fuzz/` (2 seeds)
**Dict:** none

**Input format:**

```
[cmd_type : uint32_le]  [cmd-specific fields…]
  kStart=0        → [image_size:4B][slot:1B]
  kTransfer=1     → [sequence_id:4B][offset:4B][use_actual_size:1B][data_len:2B][data…]
  kDeltaTransfer=2→ [sequence_id:4B][data_len:2B][detools_patch_bytes…]
  kFinish=3       → (no additional fields)
```

**Run locally:**

```bash
./build/host/fwup-delta-fuzz fuzz/fuzzer_inputs/fwup-delta-fuzz/
```

---

### `nfc-timer-fuzz` — BCW-05

| Finding | Description |
|---------|-------------|
| BCW-05  | Timer index not validated before array access: valid ∈ {0} ∪ [1000, 1009] |

**Source:** `firmware/hal/nfc/src/embedded/nfc_timer_fuzz.cc`
**Meson:** `firmware/hal/nfc/meson.build`
**Seeds:** `fuzz/fuzzer_inputs/nfc-timer-fuzz/` (6 seeds)
**Dict:** `fuzz/fuzzer_inputs/nfc-timer-fuzz/nfc_timer.dict`

**Input format** (repeated):

```
[index : uint32_le]  [use_expired : bool(1B)]
```

`index == 0` is the ST-RFAL special case (safe).
`1000 ≤ index ≤ 1009` maps to `timers[0..9]` (valid).
All other values trigger `ASSERT` → `SIGILL`.

**Run locally:**

```bash
./build/host/nfc-timer-fuzz \
  -dict=fuzz/fuzzer_inputs/nfc-timer-fuzz/nfc_timer.dict \
  fuzz/fuzzer_inputs/nfc-timer-fuzz/
```

---

### `touch-decode-fuzz` — BCW-40

| Finding | Description |
|---------|-------------|
| BCW-40  | `_touch_decode_data()` accesses `touch[0]` before checking `raw_points > 0`; heap-buffer-overflow when `num_points==0` allocates only 2 bytes |

**Source:** `firmware/hal/touch/src/touch_decode_fuzz.cc`
**Meson:** `firmware/hal/touch/meson.build`
**Seeds:** `fuzz/fuzzer_inputs/touch-decode-fuzz/` (2 seeds)
**Dict:** none

**Input format:**

```
[num_points : uint8 in [0,2]]  [gesture : uint8]  [raw_points : uint8]
[touch_point_0 : 6B]  [touch_point_1 : 6B (if num_points==2)]
```

ASAN catches heap-buffer-overflow on `touch[0]` access when `num_points==0`.

**Run locally:**

```bash
./build/host/touch-decode-fuzz fuzz/fuzzer_inputs/touch-decode-fuzz/
```

---

### `indexfs-addr-fuzz` — BCW-29

| Finding | Description |
|---------|-------------|
| BCW-29  | `addr_in_range()` unsigned-subtraction: `addr < range_start` wraps to large uint32 making the check pass |

**Source:** `firmware/lib/indexfs/indexfs_addr_fuzz.cc`
**Meson:** `firmware/lib/indexfs/meson.build`
**Seeds:** `fuzz/fuzzer_inputs/indexfs-addr-fuzz/` (5 seeds)
**Dict:** `fuzz/fuzzer_inputs/indexfs-addr-fuzz/boundary.dict`

**Input format** (repeated):

```
[addr : uint32_le]  [range_start : uint32_le]  [range_size : uint32_le]
```

Property invariants checked on every triple — any violation triggers `ASSERT` → `SIGILL`.

**Run locally:**

```bash
./build/host/indexfs-addr-fuzz \
  -dict=fuzz/fuzzer_inputs/indexfs-addr-fuzz/boundary.dict \
  fuzz/fuzzer_inputs/indexfs-addr-fuzz/
```

---

## ClusterFuzz Integration

`fuzz/build.sh` packages all targets for ClusterFuzz upload:

```bash
# Local dry-run (writes to firmware/fuzz/build/out/)
bash firmware/fuzz/build.sh

# With explicit output directory (as ClusterFuzz would invoke it)
OUT=/tmp/fuzz-out bash firmware/fuzz/build.sh
```

The script:
1. Runs `inv fuzz` to build all targets.
2. Copies each `*-fuzz` binary to `$OUT/`.
3. Zips seed corpora to `$OUT/<target>_seed_corpus.zip`.
4. Copies dictionaries to `$OUT/<target>.dict`.

---

## Coverage Reporting

See `fuzz/coverage/README.md` for instructions on generating LLVM coverage
reports and uploading to ClusterFuzz.

```bash
bash firmware/fuzz/coverage/coverage.sh
```

---

## `fuzz_assert.h` — ASSERT Override

Firmware's `ASSERT(expr)` calls `exit(9876)` on host builds, which kills the
libFuzzer process rather than being caught as a crash.

`fuzz/fuzz_assert.h` overrides `ASSERT` with `__builtin_trap()` (SIGILL) after
all firmware headers are included. libFuzzer catches SIGILL and saves the
crashing input. Include it **last** in any harness that exercises code
containing `ASSERT`.

```c
/* In your _fuzz.cc, after all other includes: */
extern "C" {
#include "fuzz/fuzz_assert.h"
}
```

---

## Finding Reference

| BCW   | Target                    | Sanitizer trigger         |
|-------|---------------------------|---------------------------|
| BCW-01 | wca-session-fuzz         | ASAN heap-buffer-overflow |
| BCW-04 | wca-session-fuzz         | ASAN heap-buffer-overflow |
| BCW-05 | nfc-timer-fuzz           | SIGILL (ASSERT)           |
| BCW-06 | wca-session-fuzz         | ASAN heap-buffer-overflow |
| BCW-07 | wca-proto-handlers-fuzz  | ASAN null-dereference     |
| BCW-08 | secure-channel-fuzz      | ASAN heap-buffer-overflow |
| BCW-09 | wca-session-fuzz         | Logic / state corruption  |
| BCW-10 | wca-session-fuzz         | Logic / state corruption  |
| BCW-15 | secure-channel-fuzz      | ASAN heap-buffer-overflow |
| BCW-16 | secure-channel-fuzz      | ASAN heap-buffer-overflow |
| BCW-19 | wca-proto-handlers-fuzz  | Logic / memory access     |
| BCW-25 | fwup-delta-fuzz          | UBSan signed-int-overflow |
| BCW-26 | fwup-delta-fuzz          | ASAN heap-buffer-overflow |
| BCW-29 | indexfs-addr-fuzz        | SIGILL (ASSERT)           |
| BCW-31 | wca-proto-handlers-fuzz  | SIGILL (ASSERT)           |
| BCW-36 | fwup-delta-fuzz          | Logic / state corruption  |
| BCW-40 | touch-decode-fuzz        | ASAN heap-buffer-overflow |
