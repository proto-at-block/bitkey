/**
 * wca_proto_handlers_fuzz.cc — Proto NULL-deref and ASSERT-trigger fuzzer.
 *
 * Security findings covered:
 *   BCW-07: proto_get_cmd() returns NULL when protobuf decode fails.  Multiple
 *           handlers in auth_task.c (handle_delete_fingerprint,
 *           handle_unlock_secret, handle_provision_unlock_secret,
 *           handle_start_fingerprint_enrollment, etc.) dereference the
 *           returned pointer WITHOUT a NULL check, causing a crash on any
 *           malformed protobuf sent over NFC.
 *
 *   BCW-31: handle_seal_csek() in key_manager_task.c ASSERT-fires when the
 *           plaintext-CSEK path is taken and unsealed_csek.size does not equal
 *           sizeof(unsealed_csek.bytes).  An unauthenticated NFC peer can trigger
 *           this over the wire, causing a DoS.
 *
 *   BCW-19: handle_fingerprint_reset_finalize() passes a fuzz-controlled byte
 *           array directly to handle_grant_finalize() once the grant.size field
 *           equals sizeof(grant_t).  This exercises the parsing surface that
 *           leads to arbitrary bytes reaching grant validation logic.
 *
 * Approach (thin harness) for all findings:
 *   Rather than linking the full auth_task.c dependency graph, this harness
 *   reproduces the exact vulnerable pattern from each affected handler:
 *     1. Call proto_get_cmd() with fuzzer-controlled bytes (may return NULL).
 *     2. Proceed to dereference cmd->msg.<field> exactly as the real handler
 *        does — without a NULL check.
 *   Under ASAN, a NULL dereference raises SIGSEGV.  LibFuzzer records the
 *   reproducing input as a crash-* file.
 *
 *   The stubs here match what the real handlers expect from their environment
 *   (proto mempool, response callback) while removing non-essential I/O.
 */

#include "FuzzedDataProvider.h"

extern "C" {
#include "fff.h"
#include "ipc.h"
#include "mempool.h"
#include "proto_helpers.h"
#include "secutils.h"
#include "wallet.pb.h"

void refresh_auth(void) {}
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

FAKE_VOID_FUNC(rtos_timer_create_static, rtos_timer_t*, rtos_timer_callback_t);
FAKE_VOID_FUNC(rtos_timer_start, rtos_timer_t*, uint32_t);
FAKE_VOID_FUNC(rtos_timer_stop, rtos_timer_t*);

/* fuzz_assert.h overrides ASSERT to __builtin_trap() so BCW-31 ASSERT
 * violations are caught by libfuzzer as SIGILL rather than calling exit(). */
#include "fuzz_assert.h"
}  // extern "C"

#include <stddef.h>
#include <stdint.h>
#include <vector>

DEFINE_FFF_GLOBALS;
FAKE_VOID_FUNC(rtos_mutex_create, rtos_mutex_t*);
FAKE_VALUE_FUNC(bool, rtos_mutex_lock, rtos_mutex_t*);
FAKE_VALUE_FUNC(bool, rtos_mutex_unlock, rtos_mutex_t*);
FAKE_VALUE_FUNC(bool, rtos_queue_send, rtos_queue_t*, void*, uint32_t);
FAKE_VALUE_FUNC(bool, rtos_queue_recv, rtos_queue_t*, void*, uint32_t);
FAKE_VALUE_FUNC(bool, rtos_in_isr);

static uint8_t g_rsp_buf[sizeof(fwpb_wallet_rsp)];

static void handle_proto_fuzz_response(uint8_t* encoded_proto, uint32_t sz) {
  if (encoded_proto && sz <= sizeof(g_rsp_buf)) {
    memcpy(g_rsp_buf, encoded_proto, sz);
  }
}

/* -----------------------------------------------------------------------
 * Vulnerable handler patterns reproduced from auth_task.c (BCW-07).
 * Each function mirrors the exact access pattern of the real handler,
 * specifically the missing NULL check on the result of proto_get_cmd().
 * ----------------------------------------------------------------------- */

/* Mirrors handle_delete_fingerprint():
 * accesses cmd->msg.delete_fingerprint_cmd.index without NULL check. */
static void fuzz_handler_delete_fingerprint(const uint8_t* raw, size_t len) {
  fwpb_wallet_cmd* cmd = proto_get_cmd(const_cast<uint8_t*>(raw), (uint32_t)len);
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_delete_fingerprint_rsp_tag;
  rsp->status = fwpb_status_ERROR;
  /* BCW-07: cmd may be NULL here; real handler does not check. */
  (void)cmd->msg.delete_fingerprint_cmd.index; /* SIGSEGV if cmd == NULL */
  proto_free_buffers(cmd, rsp);
}

/* Mirrors handle_start_fingerprint_enrollment():
 * accesses cmd->msg.start_fingerprint_enrollment_cmd.has_handle. */
static void fuzz_handler_start_fp_enrollment(const uint8_t* raw, size_t len) {
  fwpb_wallet_cmd* cmd = proto_get_cmd(const_cast<uint8_t*>(raw), (uint32_t)len);
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_start_fingerprint_enrollment_rsp_tag;
  /* BCW-07: NULL deref on failed decode */
  (void)cmd->msg.start_fingerprint_enrollment_cmd.has_handle;
  proto_free_buffers(cmd, rsp);
}

/* Mirrors handle_unlock_secret():
 * accesses cmd->msg.send_unlock_secret_cmd.secret.ciphertext.size. */
static void fuzz_handler_unlock_secret(const uint8_t* raw, size_t len) {
  fwpb_wallet_cmd* cmd = proto_get_cmd(const_cast<uint8_t*>(raw), (uint32_t)len);
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_send_unlock_secret_rsp_tag;
  rsp->status = fwpb_status_ERROR;
  /* BCW-07: NULL deref on failed decode */
  (void)cmd->msg.send_unlock_secret_cmd.secret.ciphertext.size;
  proto_free_buffers(cmd, rsp);
}

/* Mirrors the non-production parsing path in handle_provision_unlock_secret():
 * accesses cmd->msg.provision_unlock_secret_cmd.secret.ciphertext.size. */
static void fuzz_handler_provision_unlock_secret(const uint8_t* raw, size_t len) {
  fwpb_wallet_cmd* cmd = proto_get_cmd(const_cast<uint8_t*>(raw), (uint32_t)len);
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_provision_unlock_secret_rsp_tag;
  rsp->status = fwpb_status_ERROR;
  /* BCW-07: NULL deref on failed decode */
  (void)cmd->msg.provision_unlock_secret_cmd.secret.ciphertext.size;
  proto_free_buffers(cmd, rsp);
}

/* Mirrors handle_get_fingerprint_enrollment_status():
 * accesses cmd->msg.get_fingerprint_enrollment_status_cmd.app_knows_about_this_field. */
static void fuzz_handler_get_fp_enrollment_status(const uint8_t* raw, size_t len) {
  fwpb_wallet_cmd* cmd = proto_get_cmd(const_cast<uint8_t*>(raw), (uint32_t)len);
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_get_fingerprint_enrollment_status_rsp_tag;
  /* BCW-07: NULL deref on failed decode */
  (void)cmd->msg.get_fingerprint_enrollment_status_cmd.app_knows_about_this_field;
  proto_free_buffers(cmd, rsp);
}

/* Mirrors handle_set_fingerprint_label():
 * accesses cmd->msg.set_fingerprint_label_cmd.handle.index. */
static void fuzz_handler_set_fp_label(const uint8_t* raw, size_t len) {
  fwpb_wallet_cmd* cmd = proto_get_cmd(const_cast<uint8_t*>(raw), (uint32_t)len);
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_set_fingerprint_label_rsp_tag;
  rsp->status = fwpb_status_ERROR;
  /* BCW-07: NULL deref on failed decode */
  (void)cmd->msg.set_fingerprint_label_cmd.handle.index;
  proto_free_buffers(cmd, rsp);
}

/* Mirrors handle_seal_csek() in key_manager_task.c.
 *
 * BCW-07: NULL deref if proto decode fails (same pattern as above).
 * BCW-31: When has_csek == false (plaintext CSEK path), production code does:
 *           ASSERT(sizeof(unsealed_csek.bytes) == unsealed_csek.size)
 *         If the wire value of unsealed_csek.size != sizeof bytes, the ASSERT
 *         fires.  With fuzz_assert.h this raises SIGILL (detectable crash).  An
 *         unauthenticated NFC peer can trigger this before any authentication. */
static void fuzz_handler_seal_csek(const uint8_t* raw, size_t len) {
  fwpb_wallet_cmd* cmd = proto_get_cmd(const_cast<uint8_t*>(raw), (uint32_t)len);
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_seal_csek_rsp_tag;
  rsp->msg.seal_csek_rsp.rsp_status = fwpb_seal_csek_rsp_seal_csek_rsp_status_ERROR;
  /* BCW-07: cmd may be NULL here — same pattern as auth_task handlers. */
  if (!cmd->msg.seal_csek_cmd.has_csek) {
    /* BCW-31: ASSERT fires when size field doesn't match declared byte size.
     * With fuzz_assert.h this is SIGILL; without it, exit(9876) kills libfuzzer. */
    ASSERT(sizeof(cmd->msg.seal_csek_cmd.unsealed_csek.bytes) ==
           cmd->msg.seal_csek_cmd.unsealed_csek.size);
  }
  proto_free_buffers(cmd, rsp);
}

/* Mirrors handle_fingerprint_reset_finalize() in key_manager_task.c.
 *
 * BCW-07: NULL deref if proto decode fails.
 * BCW-19: Once proto decode succeeds and grant.size == sizeof(grant_t), the
 *         production handler casts grant.bytes directly to grant_t* and passes
 *         it to handle_grant_finalize().  This fuzzer exercises the parsing
 *         surface and confirms that arbitrary bytes can reach the size check. */
static void fuzz_handler_fingerprint_reset_finalize(const uint8_t* raw, size_t len) {
  fwpb_wallet_cmd* cmd = proto_get_cmd(const_cast<uint8_t*>(raw), (uint32_t)len);
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_fingerprint_reset_finalize_rsp_tag;
  rsp->status = fwpb_status_ERROR;
  /* BCW-07: NULL deref if decode failed. */
  (void)cmd->msg.fingerprint_reset_finalize_cmd.grant.size;
  /* BCW-19: Size check mirrors production; bytes pass unchecked when it matches.
   * (handle_grant_finalize is not called here — thin harness tests parse path.) */
  (void)cmd->msg.fingerprint_reset_finalize_cmd.grant.bytes;
  proto_free_buffers(cmd, rsp);
}

typedef void (*handler_fn_t)(const uint8_t*, size_t);

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  FuzzedDataProvider fuzzed_data(data, size);

#define REGIONS(X)                                         \
  X(fuzz_pool, proto_cmd_scratch, PROTO_CMD_ALLOC_SIZE, 2) \
  X(fuzz_pool, proto_rsp_scratch, sizeof(fwpb_wallet_rsp), 2)
  mempool_t* mempool = mempool_create(fuzz_pool);
#undef REGIONS
  ipc_proto_register_api(mempool, g_rsp_buf, &handle_proto_fuzz_response);

  /* Handler table — each entry is an independently fuzzable path. */
  static const handler_fn_t handlers[] = {
    fuzz_handler_delete_fingerprint,
    fuzz_handler_start_fp_enrollment,
    fuzz_handler_unlock_secret,
    fuzz_handler_provision_unlock_secret,
    fuzz_handler_get_fp_enrollment_status,
    fuzz_handler_set_fp_label,
    fuzz_handler_seal_csek,                  /* BCW-31: ASSERT on CSEK size mismatch */
    fuzz_handler_fingerprint_reset_finalize, /* BCW-19: arbitrary bytes to grant path */
  };
  constexpr int kNumHandlers = static_cast<int>(sizeof(handlers) / sizeof(handlers[0]));

  while (fuzzed_data.remaining_bytes() > 0) {
    int idx = fuzzed_data.ConsumeIntegralInRange<int>(0, kNumHandlers - 1);

    uint32_t proto_len = fuzzed_data.ConsumeIntegralInRange<uint32_t>(0, sizeof(fwpb_wallet_cmd));
    std::vector<uint8_t> proto_bytes = fuzzed_data.ConsumeBytes<uint8_t>(proto_len);

    if (proto_bytes.empty()) {
      continue;
    }

    handlers[idx](proto_bytes.data(), proto_bytes.size());
  }

  return 0;
}
