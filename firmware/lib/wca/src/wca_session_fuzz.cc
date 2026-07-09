/**
 * wca_session_fuzz.cc — Stateful multi-session WCA fuzzer.
 *
 * Security findings covered:
 *   BCW-01: APDU length == 4 considered valid, but cmd[4] (LC) is then read
 *           out-of-bounds.  This fuzzer uses tight heap-allocated command
 *           buffers (via std::vector) so ASAN detects the OOB read.  The
 *           existing wca_fuzz.cc uses a 250-byte stack array, masking the bug.
 *   BCW-04: Regression coverage for the handle_proto_response response-buffer
 *           boundary. A response exactly equal to RESPONSE_BUFFER_SIZE must be
 *           accepted and drained without asserting.
 *   BCW-06: wca_proto_cont silently truncates oversized continuation data
 *           instead of rejecting.  Covered by random-length PROTO_CONT inputs.
 *   BCW-09: Regression coverage ensuring session resets prevent stale WCA
 *           command context from being replayed via PROTO_CONT.
 *   BCW-10: Regression coverage ensuring session resets prevent stale response
 *           bytes from being drained via GET_RESPONSE.
 */

#include "FuzzedDataProvider.h"

extern "C" {
#include "fff.h"
#include "ipc.h"
#include "mempool.h"
#include "secutils.h"
#include "wca.h"

/* Stubs required for wca_init / wca_handle_command in host builds */
FAKE_VOID_FUNC(refresh_auth);
FAKE_VOID_FUNC(rtos_timer_create_static, rtos_timer_t*, rtos_timer_callback_t);
FAKE_VOID_FUNC(rtos_timer_start, rtos_timer_t*, uint32_t);
FAKE_VOID_FUNC(rtos_timer_stop, rtos_timer_t*);
FAKE_VALUE_FUNC(bool, rtos_in_isr);

secure_bool_t onboarding_complete(void) {
  return SECURE_TRUE;
}
bool bio_fingerprint_exists(void) {
  return true;
}
secure_bool_t is_authenticated(void) {
  return SECURE_TRUE;
}
secure_bool_t is_allowing_fingerprint_enrollment(void) {
  return SECURE_TRUE;
}

/* Override ASSERT after all headers are included so that ASSERT-violations
 * (BCW-04) raise SIGILL instead of calling exit(), keeping libfuzzer alive. */
#include "fuzz_assert.h"
}  // extern "C"

#include "wca_impl.h"

#include <stddef.h>
#include <stdint.h>
#include <vector>

DEFINE_FFF_GLOBALS;
FAKE_VOID_FUNC(rtos_mutex_create, rtos_mutex_t*);
FAKE_VALUE_FUNC(bool, rtos_mutex_lock, rtos_mutex_t*);
FAKE_VALUE_FUNC(bool, rtos_mutex_unlock, rtos_mutex_t*);
FAKE_VALUE_FUNC(bool, rtos_queue_send, rtos_queue_t*, void*, uint32_t);
FAKE_VALUE_FUNC(bool, rtos_queue_recv, rtos_queue_t*, void*, uint32_t);

static bool nop_sem(void) {
  return true;
}

static bool nop_sem_false(void) {
  return false;
}

/* Actions for the stateful multi-session fuzzer */
enum Action {
  kVersion = 0,
  kProto,
  kProtoCont,
  kProtoContZeroLen, /* BCW-09: explicit zero-length PROTO_CONT regression */
  kGetResponse,
  kSessionReset,        /* BCW-09/10: reset WCA session state between exchanges */
  kResponseBufferExact, /* BCW-04: exact RESPONSE_BUFFER_SIZE regression coverage */
  kNumActions,
};

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  FuzzedDataProvider fuzzed_data(data, size);

#define REGIONS(X) X(fuzz_pool, proto_scratch, sizeof(fwpb_wallet_cmd), 3)
  mempool_t* mempool = mempool_create(fuzz_pool);
  wca_api_t api = {
    .mempool = mempool,
    .sem_take = nop_sem,
    .sem_take_nowait = nop_sem_false,
    .sem_give = nop_sem,
  };
  wca_init(&api);
#undef REGIONS

  while (fuzzed_data.remaining_bytes() > 0) {
    int action_raw = fuzzed_data.ConsumeIntegralInRange<int>(0, kNumActions - 1);
    Action action = static_cast<Action>(action_raw);

    if (action == kSessionReset) {
      /* BCW-09/BCW-10: Reinitialize WCA to model a new NFC session. wca_init()
       * resets command/response state, so any follow-up PROTO_CONT or
       * GET_RESPONSE must fail closed instead of surfacing stale data. */
      wca_init(&api);
      continue;
    }

    if (action == kResponseBufferExact) {
      /* BCW-04: Directly exercise the handle_proto_response callback with a
       * payload at the RESPONSE_BUFFER_SIZE boundary so fuzzing catches any
       * regression back to a strict less-than assertion. */
      static uint8_t resp_data[RESPONSE_BUFFER_SIZE + 1];
      uint32_t resp_size =
        fuzzed_data.ConsumeIntegralInRange<uint32_t>(0, RESPONSE_BUFFER_SIZE + 1);
      /* ipc_proto_send_response_buffer calls the registered callback, which is
       * handle_proto_response inside wca.c. */
      ipc_proto_send_response_buffer(resp_data, resp_size);
      continue;
    }

    /* BCW-01: Use a heap-allocated, exactly-sized buffer.  With ASAN enabled,
     * accessing cmd[4] when the buffer is only 4 bytes long is caught as a
     * heap-buffer-overflow.  The original wca_fuzz.cc used a 250-byte stack
     * array and could never detect this OOB read. */
    uint32_t desired_cmd_len = fuzzed_data.ConsumeIntegralInRange<uint32_t>(0, 260);
    std::vector<uint8_t> cmd_bytes = fuzzed_data.ConsumeBytes<uint8_t>(desired_cmd_len);

    /* Populate well-known APDU header bytes so the switch in wca_handle_command
     * dispatches to the intended handler. */
    if (!cmd_bytes.empty()) {
      cmd_bytes[CLA] = WCA_CLA;
    }
    if (cmd_bytes.size() >= 2) {
      switch (action) {
        case kVersion:
          cmd_bytes[INS] = WCA_INS_VERSION;
          break;
        case kProto:
          cmd_bytes[INS] = WCA_INS_PROTO;
          break;
        case kProtoCont:
        case kProtoContZeroLen:
          cmd_bytes[INS] = WCA_INS_PROTO_CONT;
          break;
        case kGetResponse:
          cmd_bytes[INS] = WCA_INS_GET_RESPONSE;
          break;
        default:
          break;
      }
    }

    if (action == kProtoContZeroLen && cmd_bytes.size() >= 5) {
      /* BCW-09: Force Lc = 0 (zero-length PROTO_CONT). Combined with session
       * resets, this exercises the stale-command replay guard. */
      cmd_bytes[LC] = 0x00;
    }

    /* Ensure rsp buffer always satisfies the ASSERT(*rsp_len >= SW_SIZE). */
    uint32_t rsp_len = 256;
    std::vector<uint8_t> rsp_bytes(rsp_len, 0);

    wca_handle_command(cmd_bytes.data(), static_cast<uint32_t>(cmd_bytes.size()), rsp_bytes.data(),
                       &rsp_len);
  }

  return 0;
}
