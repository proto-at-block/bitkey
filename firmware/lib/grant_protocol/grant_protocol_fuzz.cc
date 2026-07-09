/**
 * grant_protocol_fuzz.cc — Grant protocol verification fuzzer.
 *
 * Drives grant_protocol_verify_grant() with arbitrary grant_t byte content,
 * exercising the request-matching, app-signature verification, and WSM
 * signature-verification code paths.  src/grant_protocol.c is compiled
 * directly so the storage functions can be stubbed without overriding symbols
 * from grant_protocol_storage.c.
 *
 * External dependencies:
 *   - Storage functions (grant_storage_*): inline stubs; the harness controls
 *     whether storage returns a matching request or an error, reaching all
 *     verifier branches.
 *   - Secp256k1 verification: provided by crypto_deps['posix'].  The real
 *     implementation will always reject fuzz-generated invalid signatures.
 *   - RTOS / IPC / sysinfo: inline no-op stubs.
 */

#include "FuzzedDataProvider.h"

extern "C" {
#include "attributes.h"
#include "ecc.h"
#include "fff.h"
#include "grant_protocol.h"
#include "grant_protocol_storage_impl.h"
#include "ipc.h"
#include "rtos.h"
/* Must be included last to override ASSERT with __builtin_trap(). */
#include "fuzz_assert.h"

DEFINE_FFF_GLOBALS;

/* --- Storage stubs --------------------------------------------------------
 * Compile grant_protocol.c directly so these definitions resolve its
 * storage-function call sites without linking grant_protocol_storage.c.
 * -------------------------------------------------------------------------- */

/* Controlled by the fuzz harness for each iteration. */
static grant_request_t g_stored_request;
static bool            g_storage_has_request = false;
static bool            g_storage_has_pubkey  = false;

grant_protocol_result_t grant_storage_read_request(grant_request_t* out) {
  if (!g_storage_has_request) {
    return GRANT_RESULT_ERROR_STORAGE;
  }
  *out = g_stored_request;
  return GRANT_RESULT_OK;
}
grant_protocol_result_t grant_storage_write_request(const grant_request_t* UNUSED(r)) {
  return GRANT_RESULT_OK;
}
grant_protocol_result_t grant_storage_delete_request(void) {
  return GRANT_RESULT_OK;
}
bool grant_storage_read_app_auth_pubkey(uint8_t* pubkey) {
  if (!g_storage_has_pubkey) return false;
  /* Return a fixed dummy compressed public key (33 bytes). */
  pubkey[0] = 0x02;
  for (int i = 1; i < 33; i++) pubkey[i] = (uint8_t)i;
  return true;
}
bool grant_storage_write_app_auth_pubkey(const uint8_t* UNUSED(k)) { return true; }
bool grant_storage_app_auth_pubkey_exists(void) { return g_storage_has_pubkey; }
bool grant_storage_delete_app_auth_pubkey(void) { return true; }

/* sysinfo_chip_id_read is extern'd in grant_protocol.c (no POSIX impl). */
void sysinfo_chip_id_read(uint8_t* chip_id_out, uint32_t* length_out) {
  if (chip_id_out && length_out && *length_out >= GRANT_DEVICE_ID_LEN) {
    for (uint32_t i = 0; i < GRANT_DEVICE_ID_LEN; i++) chip_id_out[i] = 0xAB;
  }
  if (length_out) *length_out = GRANT_DEVICE_ID_LEN;
}

/* --- RTOS / IPC stubs ----------------------------------------------------- */

FAKE_VALUE_FUNC(bool, rtos_queue_send, rtos_queue_t*, void*, uint32_t);
FAKE_VALUE_FUNC(bool, rtos_queue_recv, rtos_queue_t*, void*, uint32_t);
FAKE_VOID_FUNC(rtos_mutex_create, rtos_mutex_t*);
FAKE_VOID_FUNC(rtos_event_group_create, rtos_event_group_t*);
FAKE_VOID_FUNC(rtos_semaphore_create, rtos_semaphore_t*);
FAKE_VOID_FUNC(rtos_timer_create_static, rtos_timer_t*, rtos_timer_callback_t);
FAKE_VOID_FUNC(rtos_timer_start, rtos_timer_t*, uint32_t);
FAKE_VOID_FUNC(rtos_timer_stop, rtos_timer_t*);
FAKE_VALUE_FUNC(uint32_t, rtos_event_group_set_bits, rtos_event_group_t*, uint32_t);
FAKE_VALUE_FUNC(uint32_t, rtos_event_group_wait_bits, rtos_event_group_t*,
                uint32_t, bool, bool, uint32_t);
FAKE_VALUE_FUNC(uint32_t, rtos_event_group_clear_bits, rtos_event_group_t*, uint32_t);
FAKE_VALUE_FUNC(bool, rtos_event_group_set_bits_from_isr, rtos_event_group_t*,
                uint32_t, bool*);

bool rtos_mutex_lock(rtos_mutex_t* UNUSED(t)) { return true; }
bool rtos_mutex_take(rtos_mutex_t* UNUSED(a), uint32_t UNUSED(b)) { return true; }
bool rtos_mutex_unlock(rtos_mutex_t* UNUSED(a)) { return true; }
bool rtos_in_isr(void) { return false; }
bool rtos_semaphore_give(rtos_semaphore_t* UNUSED(s)) { return true; }
bool rtos_semaphore_take(rtos_semaphore_t* UNUSED(s), uint32_t UNUSED(t)) { return true; }
void detect_glitch(void) {}
uint32_t rtos_event_group_get_bits(rtos_event_group_t* UNUSED(g)) { return 0; }
bool bd_error_str(char* UNUSED(s), const size_t UNUSED(n), const int UNUSED(e)) { return true; }

/* grant_protocol.c is compiled for this fuzz target with _ipc_send renamed to
 * this harness function. The real _ipc_send expects initialized IPC ports; the
 * grant-protocol fuzzer only exercises verification logic, so sends are no-ops
 * in the fuzz context. */
bool grant_fuzz_ipc_send(ipc_port_t port, ipc_ref_t* ref, ipc_options_t options) {
  (void)port; (void)ref; (void)options;
  return true;
}

/* Anti-glitch timing hooks require secutils initialization in firmware. They
 * are not part of the grant verification behavior being fuzzed, so make them
 * no-ops in the harness. */
void grant_fuzz_secure_glitch_random_delay(void) {}
void grant_fuzz_secure_glitch_detect(void) {}

/* --- Thread / filesystem / bio / unlock stubs --------------------------------
 * These symbols are pulled in transitively via ipc_dep → onboarding_dep
 * (onboarding.c) and wallet_dep → fs_dep (filesystem.c).  The grant-protocol
 * fuzzer stubs them as no-ops; the verification path under test does not
 * exercise filesystem initialisation or biometric/unlock logic.
 * --------------------------------------------------------------------------- */

/* filesystem.c creates a dedicated RTOS thread on init */
typedef void (*rtos_thread_cb_t)(void*);
FAKE_VOID_FUNC(rtos_thread_create_static, rtos_thread_t*, rtos_thread_cb_t, const char*, void*,
               rtos_thread_priority_t, uint32_t*, uint32_t, StaticTask_t*, rtos_thread_mpu_t);
FAKE_VOID_FUNC(rtos_thread_delete, rtos_thread_t*);

/* rtos semaphore ISR variants used by filesystem.c */
bool rtos_semaphore_take_from_isr(rtos_semaphore_t* UNUSED(s)) { return true; }
bool rtos_semaphore_give_from_isr(rtos_semaphore_t* UNUSED(s)) { return true; }

/* block device stubs — bd_mount/bd_erase_all called from filesystem.c;
 * lfs_t is typedef struct lfs lfs_t (littlefs); return NULL is safe here. */
struct lfs;
struct lfs* bd_mount(void) { return NULL; }
int         bd_erase_all(void) { return 0; }

/* biometric storage stubs (onboarding.c) */
bool bio_fingerprint_exists(void) { return false; }
void bio_wipe_state(void) {}

/* unlock stubs (onboarding.c) — unlock_err_t is int-compatible; 0 == UNLOCK_OK */
int  unlock_secret_exists(bool* exists) {
  if (exists) *exists = false;
  return 0;
}
void unlock_wipe_state(void) {}

}  // extern "C"

/* -------------------------------------------------------------------------- */

/* Use test (non-production) WIK key so grant_protocol_init can proceed. */
static const bool kInit = []() -> bool {
  crypto_ecc_secp256k1_init();
  grant_protocol_init(/* is_production= */ false);
  return true;
}();

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  (void)kInit;

  if (size < sizeof(grant_t)) {
    return 0;
  }

  FuzzedDataProvider fuzzed_data(data, size);

  /* Consume a grant_t worth of bytes as the candidate grant. */
  std::vector<uint8_t> grant_bytes =
    fuzzed_data.ConsumeBytes<uint8_t>(sizeof(grant_t));
  grant_bytes.resize(sizeof(grant_t), 0);
  const grant_t* grant = reinterpret_cast<const grant_t*>(grant_bytes.data());

  /* Control whether storage has a request and pubkey. */
  g_storage_has_request = fuzzed_data.ConsumeBool();
  g_storage_has_pubkey  = fuzzed_data.ConsumeBool();

  if (g_storage_has_request) {
    /* Optionally plant the serialized_request so the matching path is hit. */
    if (fuzzed_data.ConsumeBool()) {
      memcpy(&g_stored_request, grant->serialized_request, sizeof(grant_request_t));
    } else {
      std::vector<uint8_t> req_bytes =
        fuzzed_data.ConsumeBytes<uint8_t>(sizeof(grant_request_t));
      req_bytes.resize(sizeof(grant_request_t), 0);
      memcpy(&g_stored_request, req_bytes.data(), sizeof(grant_request_t));
    }
  }

  /* Reset all FFF call counts for each iteration to prevent accumulation. */
  RESET_FAKE(rtos_queue_send);
  RESET_FAKE(rtos_queue_recv);
  RESET_FAKE(rtos_mutex_create);
  RESET_FAKE(rtos_event_group_create);
  RESET_FAKE(rtos_semaphore_create);
  RESET_FAKE(rtos_timer_create_static);
  RESET_FAKE(rtos_timer_start);
  RESET_FAKE(rtos_timer_stop);
  RESET_FAKE(rtos_event_group_set_bits);
  RESET_FAKE(rtos_event_group_wait_bits);
  RESET_FAKE(rtos_event_group_clear_bits);
  RESET_FAKE(rtos_event_group_set_bits_from_isr);
  RESET_FAKE(rtos_thread_create_static);
  RESET_FAKE(rtos_thread_delete);

  (void)grant_protocol_verify_grant(grant);

  return 0;
}
