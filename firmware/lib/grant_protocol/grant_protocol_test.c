#include "bd/lfs_emubd.h"
#include "criterion_test_utils.h"
#include "fff.h"
#include "filesystem.h"
#include "grant_protocol.h"
#include "grant_protocol_storage_impl.h"
#include "hex.h"
#include "log.h"
#include "rtos.h"
#include "secutils.h"

// Define this if not already defined via includes
#ifndef SECP256K1_SEC1_KEY_SIZE
#define SECP256K1_SEC1_KEY_SIZE (33u)
#endif
#include "wallet.h"

#include <criterion/criterion.h>
#include <criterion/new/assert.h>

DEFINE_FFF_GLOBALS;
FAKE_VALUE_FUNC(bool, crypto_rand_bytes, uint8_t*, size_t);
FAKE_VOID_FUNC(_putchar, char);

FAKE_VALUE_FUNC(bool, bd_error_str, char*, const size_t, const int);
FAKE_VALUE_FUNC(int, bd_erase_all);
FAKE_VALUE_FUNC(lfs_t*, bd_mount);

FAKE_VALUE_FUNC(bool, rtos_queue_send, rtos_queue_t*, void*, uint32_t);
FAKE_VALUE_FUNC(bool, rtos_queue_recv, rtos_queue_t*, void*, uint32_t);

FAKE_VALUE_FUNC(bool, onboarding_complete);
FAKE_VALUE_FUNC(bool, is_allowing_fingerprint_enrollment);
FAKE_VALUE_FUNC(bool, is_authenticated);
FAKE_VOID_FUNC(refresh_auth);

FAKE_VOID_FUNC(rtos_timer_start, rtos_timer_t*, uint32_t);
FAKE_VOID_FUNC(rtos_timer_stop, rtos_timer_t*);
FAKE_VOID_FUNC(rtos_timer_create_static, rtos_timer_t*, rtos_timer_callback_t);

typedef void (*f_cb)(void*);
FAKE_VOID_FUNC(rtos_mutex_create, rtos_mutex_t*);
FAKE_VOID_FUNC(rtos_semaphore_create, rtos_semaphore_t*);
FAKE_VOID_FUNC(rtos_thread_create_static, rtos_thread_t*, f_cb, const char*, void*,
               rtos_thread_priority_t, uint32_t*, uint32_t, StaticTask_t*, rtos_thread_mpu_t);
FAKE_VOID_FUNC(rtos_thread_delete, rtos_thread_t*);
FAKE_VOID_FUNC(rtos_event_group_create, rtos_event_group_t*);
FAKE_VALUE_FUNC(uint32_t, rtos_event_group_set_bits, rtos_event_group_t*, const uint32_t);
FAKE_VALUE_FUNC(bool, rtos_event_group_set_bits_from_isr, rtos_event_group_t*, const uint32_t,
                bool*);
FAKE_VALUE_FUNC(uint32_t, rtos_event_group_get_bits, rtos_event_group_t*);
FAKE_VALUE_FUNC(uint32_t, rtos_event_group_wait_bits, rtos_event_group_t*, const uint32_t,
                const bool, const bool, uint32_t);
FAKE_VALUE_FUNC(uint32_t, rtos_event_group_clear_bits, rtos_event_group_t*, const uint32_t);

bool rtos_mutex_lock(rtos_mutex_t* UNUSED(t)) {
  return true;
}
bool rtos_mutex_take(rtos_mutex_t* UNUSED(a), uint32_t UNUSED(b)) {
  return true;
}
bool rtos_mutex_unlock(rtos_mutex_t* UNUSED(a)) {
  return true;
}
bool rtos_mutex_lock_from_isr(rtos_mutex_t* UNUSED(t)) {
  return true;
}
bool rtos_mutex_unlock_from_isr(rtos_mutex_t* UNUSED(t)) {
  return true;
}
bool rtos_in_isr(void) {
  return false;
}
bool rtos_semaphore_give_from_isr(rtos_semaphore_t* UNUSED(s)) {
  return true;
}
bool rtos_semaphore_take_from_isr(rtos_semaphore_t* UNUSED(s)) {
  return true;
}
bool rtos_semaphore_give(rtos_semaphore_t* UNUSED(s)) {
  return true;
}
bool rtos_semaphore_take(rtos_semaphore_t* UNUSED(s), uint32_t UNUSED(t)) {
  return true;
}

static lfs_t lfs;
#define FS_BLOCK_CYCLES   (500)          /* Wear leveling cycles */
#define FS_LOOKAHEAD_SIZE (128)          /* Must be a multiple of 8 */
#define FLASH_PAGE_SIZE   (0x00002000UL) /* Flash Memory page size */
#define FS_BLOCKS         16u            /* Number of flash blocks */

static uint8_t lfs_read_buf[FLASH_PAGE_SIZE];
static uint8_t lfs_prog_buf[FLASH_PAGE_SIZE];
static uint8_t lfs_lookahead_buf[FS_LOOKAHEAD_SIZE];
const struct lfs_emubd_config emubd_cfg = {
  .read_size = FLASH_PAGE_SIZE,
  .prog_size = FLASH_PAGE_SIZE,
  .erase_size = FLASH_PAGE_SIZE,
  .erase_count = FS_BLOCK_COUNT,
  .erase_value = -1,
};

static lfs_emubd_t emubd = {0};
rtos_thread_mpu_t _fs_mount_task_regions;

const struct lfs_config cfg = {
  // block device operations
  .read = lfs_emubd_read,
  .prog = lfs_emubd_prog,
  .erase = lfs_emubd_erase,
  .sync = lfs_emubd_sync,

  // block device configuration
  .read_size = FLASH_PAGE_SIZE,
  .prog_size = FLASH_PAGE_SIZE,
  .block_size = FLASH_PAGE_SIZE,
  .block_count = FS_BLOCK_COUNT,
  .cache_size = FLASH_PAGE_SIZE,
  .lookahead_size = FS_LOOKAHEAD_SIZE,
  .block_cycles = FS_BLOCK_CYCLES,

  .read_buffer = lfs_read_buf,
  .prog_buffer = lfs_prog_buf,
  .lookahead_buffer = lfs_lookahead_buf,

  .context = &emubd,
};

static void init_lfs() {
  cr_assert(lfs_emubd_create(&cfg, &emubd_cfg) == 0);
  cr_assert(lfs_format(&lfs, &cfg) == 0);
  cr_assert(lfs_mount(&lfs, &cfg) == 0);
  set_lfs(&lfs);
}

void fini() {
  lfs_emubd_destroy(&cfg);
}

extern struct {
  const uint8_t* wik_pubkey;
  grant_request_t outstanding_request;
} grant_ctx;

static const uint8_t FAKE_DEVICE_ID[GRANT_DEVICE_ID_LEN] = {0x01, 0x02, 0x03, 0x04,
                                                            0x05, 0x06, 0x07, 0x08};
static const uint8_t FAKE_CHALLENGE[GRANT_CHALLENGE_LEN] = {
  0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0x00};
static extended_key_t fake_auth_key;

static const uint8_t FAKE_BIP32_SIGNATURE[64] = {0x55};
static const uint8_t FAKE_WIK_PROD_SIGNATURE[GRANT_SIGNATURE_LEN] = {0x66};
static const uint8_t FAKE_WIK_DEV_SIGNATURE[GRANT_SIGNATURE_LEN] = {0x77};
static const uint8_t FAKE_APP_SIGNATURE[GRANT_SIGNATURE_LEN] = {0x88};
static const uint8_t FAKE_APP_PUBKEY[33] = {0x02, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88,
                                            0x99, 0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF, 0x00, 0x11,
                                            0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0xAA,
                                            0xBB, 0xCC, 0xDD, 0xEE, 0xFF, 0x00};

static struct {
  bool pass_signature_verification;
  bool pass_app_signature_verification;
  bool pass_wik_signature_verification;
  bool use_separate_verification;  // When true, use separate verification flags
} test_ctx = {
  .pass_signature_verification = true,
  .pass_app_signature_verification = true,
  .pass_wik_signature_verification = true,
  .use_separate_verification = false,
};

void sysinfo_chip_id_read(uint8_t* chip_id_out, uint32_t* length_out) {
  memcpy(chip_id_out, FAKE_DEVICE_ID, GRANT_DEVICE_ID_LEN);
  *length_out = GRANT_DEVICE_ID_LEN;
}

bool wallet_get_w1_auth_key(extended_key_t* key_out) {
  memcpy(key_out, &fake_auth_key, sizeof(extended_key_t));
  return true;
}

bool bip32_sign(extended_key_t* key, uint8_t* msg, uint8_t* sig) {
  ASSERT(key != NULL && msg != NULL && sig != NULL);
  memcpy(sig, FAKE_BIP32_SIGNATURE, GRANT_SIGNATURE_LEN);
  return true;
}

bool crypto_random(uint8_t* data, uint32_t num_bytes) {
  memcpy(data, FAKE_CHALLENGE, num_bytes);
  return true;
}

// WIK public keys for comparison (defined in grant_protocol.c)
static const uint8_t WIK_TEST_PUBKEY_LOCAL[] = {
  0x03, 0x07, 0x84, 0x51, 0xe0, 0xc1, 0xe1, 0x27, 0x43, 0xd2, 0xfd,
  0xd9, 0x3a, 0xe7, 0xd0, 0x3d, 0x5c, 0xf7, 0x81, 0x3d, 0x2f, 0x61,
  0x2d, 0xe1, 0x09, 0x04, 0xe1, 0xc6, 0xa0, 0xb8, 0x7f, 0x70, 0x71,
};

static const uint8_t WIK_PROD_PUBKEY_LOCAL[] = {
  0x02, 0x95, 0x21, 0x6a, 0x2e, 0x0b, 0x54, 0xb3, 0x82, 0xcc, 0x39,
  0x38, 0xe2, 0x07, 0x29, 0x8d, 0x21, 0xcb, 0x8c, 0x5f, 0x68, 0x6f,
  0x78, 0xb0, 0x5d, 0x9f, 0x14, 0xb4, 0xe4, 0x66, 0x9e, 0x56, 0x0f,
};

bool crypto_ecc_secp256k1_pubkey_verify(const uint8_t pubkey[SECP256K1_SEC1_KEY_SIZE]) {
  // Basic validation for testing
  // Check that first byte is 0x02 or 0x03 (compressed pubkey prefix)
  if (pubkey[0] != 0x02 && pubkey[0] != 0x03) {
    return false;
  }
  // In tests, we accept our known test keys as valid
  return true;
}

bool crypto_ecc_secp256k1_verify_signature(const uint8_t pubkey[SECP256K1_KEY_SIZE],
                                           const uint8_t* message, uint32_t message_size,
                                           const uint8_t signature[ECC_SIG_SIZE]) {
  if (test_ctx.use_separate_verification) {
    // Check if this is app signature verification (pubkey matches
    // FAKE_APP_PUBKEY)
    if (memcmp(pubkey, FAKE_APP_PUBKEY, 33) == 0) {
      return test_ctx.pass_app_signature_verification;
    }
    // Check if this is WIK signature verification (check both test and prod
    // keys)
    if (memcmp(pubkey, WIK_TEST_PUBKEY_LOCAL, 33) == 0 ||
        memcmp(pubkey, WIK_PROD_PUBKEY_LOCAL, 33) == 0) {
      return test_ctx.pass_wik_signature_verification;
    }
  }
  // Default behavior for backward compatibility
  return test_ctx.pass_signature_verification;
}

void setup(void) {
  grant_ctx.wik_pubkey = NULL;
  test_ctx.pass_signature_verification = true;
  test_ctx.pass_app_signature_verification = true;
  test_ctx.pass_wik_signature_verification = true;
  test_ctx.use_separate_verification = false;
  init_lfs();
}

static void assert_grant(grant_action_t expected_action, grant_request_t* req) {
  cr_assert_eq(req->action, expected_action);
  cr_assert_eq(req->version, GRANT_PROTOCOL_VERSION);
  cr_util_cmp_buffers(req->device_id, FAKE_DEVICE_ID, GRANT_DEVICE_ID_LEN);
  cr_util_cmp_buffers(req->challenge, FAKE_CHALLENGE, GRANT_CHALLENGE_LEN);
  cr_util_cmp_buffers(req->signature, FAKE_BIP32_SIGNATURE, GRANT_SIGNATURE_LEN);
}

static void asserted_grant_request(grant_request_t* req, grant_action_t action) {
  grant_protocol_result_t res = grant_protocol_create_request(action, req);
  cr_assert_eq(res, GRANT_RESULT_OK);
  assert_grant(action, req);

  if (action == ACTION_FINGERPRINT_RESET) {
    cr_assert_eq(fs_file_exists(GRANT_REQUEST_PATH), true);
  }
}

static void mock_server_sign_grant(grant_request_t* req, grant_t* grant, bool is_production) {
  grant->version = GRANT_PROTOCOL_VERSION;
  memcpy(grant->serialized_request, req, sizeof(grant_request_t));
  memcpy(grant->app_signature, FAKE_APP_SIGNATURE, GRANT_SIGNATURE_LEN);

  if (is_production) {
    memcpy(grant->wsm_signature, FAKE_WIK_PROD_SIGNATURE, GRANT_SIGNATURE_LEN);
  } else {
    memcpy(grant->wsm_signature, FAKE_WIK_DEV_SIGNATURE, GRANT_SIGNATURE_LEN);
  }
}

static void assert_grant_deleted(void) {
  bool all_zeroes = true;

  uint8_t* req = (uint8_t*)&grant_ctx.outstanding_request;
  for (int i = 0; i < sizeof(grant_ctx.outstanding_request); i++) {
    if (req[i] != 0) {
      all_zeroes = false;
      break;
    }
  }

  cr_assert_eq(all_zeroes, true);

  cr_assert_eq(fs_file_exists(GRANT_REQUEST_PATH), false);
}

Test(grant_protocol_tests, fingerprint_reset_ok, .init = setup, .fini = fini) {
  const bool production = true;
  grant_protocol_init(production);

  // Provision app auth pubkey before creating request
  cr_assert(grant_storage_write_app_auth_pubkey(FAKE_APP_PUBKEY));

  // Generate the grant request.
  grant_request_t req;
  asserted_grant_request(&req, ACTION_FINGERPRINT_RESET);

  // Mock the server signing the grant.
  grant_t grant;
  mock_server_sign_grant(&req, &grant, production);

  // Verify the grant.
  grant_protocol_result_t res = grant_protocol_verify_grant(&grant);
  cr_assert_eq(res, GRANT_RESULT_OK);

  // Delete the grant request.
  cr_assert_eq(grant_protocol_delete_outstanding_request(), GRANT_RESULT_OK);
  assert_grant_deleted();
}

Test(grant_protocol_tests, invalid_wsm_signature, .init = setup, .fini = fini) {
  const bool production = true;
  grant_protocol_init(production);

  // Provision app auth pubkey before creating request
  cr_assert(grant_storage_write_app_auth_pubkey(FAKE_APP_PUBKEY));

  grant_request_t req;
  asserted_grant_request(&req, ACTION_FINGERPRINT_RESET);

  grant_t grant;
  mock_server_sign_grant(&req, &grant, production);

  // Enable separate verification to test WSM signature independently
  test_ctx.use_separate_verification = true;
  test_ctx.pass_app_signature_verification = true;   // App sig should pass
  test_ctx.pass_wik_signature_verification = false;  // WIK sig should fail

  // No need to corrupt the signature - our mock will make it fail
  grant_protocol_result_t res = grant_protocol_verify_grant(&grant);

  // Now we should get WIK verification error since app sig passes
  cr_assert_eq(res, GRANT_RESULT_ERROR_VERIFICATION);
}

Test(grant_protocol_tests, uses_debug_wik, .init = setup, .fini = fini) {
  const bool production = false;
  grant_protocol_init(production);

  // Provision app auth pubkey before creating request
  cr_assert(grant_storage_write_app_auth_pubkey(FAKE_APP_PUBKEY));

  grant_request_t req;
  asserted_grant_request(&req, ACTION_FINGERPRINT_RESET);

  grant_t grant;
  mock_server_sign_grant(&req, &grant, production);

  // Ensure that the debug wik is used.
  cr_util_cmp_buffers(grant.wsm_signature, FAKE_WIK_DEV_SIGNATURE, GRANT_SIGNATURE_LEN);
}

Test(grant_protocol_tests, uses_production_wik, .init = setup, .fini = fini) {
  const bool production = true;
  grant_protocol_init(production);

  // Provision app auth pubkey before creating request
  cr_assert(grant_storage_write_app_auth_pubkey(FAKE_APP_PUBKEY));

  grant_request_t req;
  asserted_grant_request(&req, ACTION_FINGERPRINT_RESET);

  grant_t grant;
  mock_server_sign_grant(&req, &grant, production);

  // Ensure that the production wik is used.
  cr_util_cmp_buffers(grant.wsm_signature, FAKE_WIK_PROD_SIGNATURE, GRANT_SIGNATURE_LEN);
}

Test(grant_protocol_tests, prevents_replays, .init = setup, .fini = fini) {
  const bool production = true;
  grant_protocol_init(production);

  // Provision app auth pubkey
  cr_assert(grant_storage_write_app_auth_pubkey(FAKE_APP_PUBKEY));

  grant_request_t req;
  asserted_grant_request(&req, ACTION_FINGERPRINT_RESET);

  // A valid grant request from the past, with a different challenge.
  grant_request_t replayed_req = {
    .version = GRANT_PROTOCOL_VERSION,
    .action = ACTION_FINGERPRINT_RESET,
  };
  memcpy(replayed_req.device_id, FAKE_DEVICE_ID, GRANT_DEVICE_ID_LEN);
  memset(replayed_req.challenge, 0x11, GRANT_CHALLENGE_LEN);
  memcpy(replayed_req.signature, FAKE_BIP32_SIGNATURE, GRANT_SIGNATURE_LEN);

  grant_t grant;
  mock_server_sign_grant(&req, &grant, production);

  grant_t replayed_grant;
  mock_server_sign_grant(&replayed_req, &replayed_grant, production);

  grant_protocol_result_t res = grant_protocol_verify_grant(&replayed_grant);
  cr_assert_eq(res, GRANT_RESULT_ERROR_REQUEST_MISMATCH);
}

Test(grant_protocol_tests, prevents_substitution_attack, .init = setup, .fini = fini) {
  const bool production = true;
  grant_protocol_init(production);

  // Provision app auth pubkey
  cr_assert(grant_storage_write_app_auth_pubkey(FAKE_APP_PUBKEY));

  // Victim's device generates a request.
  grant_request_t victim_req;
  asserted_grant_request(&victim_req, ACTION_FINGERPRINT_RESET);

  // Attacker's device generates a request.
  grant_request_t attacker_req;
  asserted_grant_request(&attacker_req, ACTION_FINGERPRINT_RESET);
  // Replace the challenge, id and signature here, as they would be different
  // from the victim's.
  attacker_req.challenge[0] ^= 0x01;
  attacker_req.signature[0] ^= 0x01;
  attacker_req.device_id[0] ^= 0x01;

  // Now, sign the attacker's request.
  grant_t attacker_grant;
  mock_server_sign_grant(&attacker_req, &attacker_grant, production);

  // Attacker then forwards the signed attacker grant to the victim.
  // The victim verifies the grant and should reject it.

  grant_protocol_result_t res = grant_protocol_verify_grant(&attacker_grant);
  cr_assert_eq(res, GRANT_RESULT_ERROR_REQUEST_MISMATCH);
}

Test(grant_protocol_tests, grant_already_consumed, .init = setup, .fini = fini) {
  const bool production = true;
  grant_protocol_init(production);

  // Provision app auth pubkey
  cr_assert(grant_storage_write_app_auth_pubkey(FAKE_APP_PUBKEY));

  // Generate the grant request.
  grant_request_t req;
  asserted_grant_request(&req, ACTION_FINGERPRINT_RESET);

  // Mock the server signing the grant.
  grant_t grant;
  mock_server_sign_grant(&req, &grant, production);

  // First verification should succeed
  grant_protocol_result_t res = grant_protocol_verify_grant(&grant);
  cr_assert_eq(res, GRANT_RESULT_OK);

  // Delete the grant request (simulating consumption)
  cr_assert_eq(grant_protocol_delete_outstanding_request(), GRANT_RESULT_OK);
  assert_grant_deleted();

  // Second verification should fail with STORAGE error (file not found)
  res = grant_protocol_verify_grant(&grant);
  cr_assert_eq(res, GRANT_RESULT_ERROR_STORAGE);
}

Test(grant_protocol_tests, no_app_auth_pubkey, .init = setup, .fini = fini) {
  const bool production = true;
  grant_protocol_init(production);

  // Do NOT provision app auth pubkey to test the failure case

  // Try to create a fingerprint reset request without app auth pubkey
  grant_request_t req;
  grant_protocol_result_t res = grant_protocol_create_request(ACTION_FINGERPRINT_RESET, &req);

  // Should fail because no app auth pubkey is provisioned
  cr_assert_eq(res, GRANT_RESULT_ERROR_NO_APP_PUBKEY);
}

Test(grant_protocol_tests, both_signatures_valid, .init = setup, .fini = fini) {
  const bool production = true;
  grant_protocol_init(production);

  // Provision app auth pubkey
  cr_assert(grant_storage_write_app_auth_pubkey(FAKE_APP_PUBKEY));

  grant_request_t req;
  asserted_grant_request(&req, ACTION_FINGERPRINT_RESET);

  grant_t grant;
  mock_server_sign_grant(&req, &grant, production);

  // Enable separate verification with both signatures passing
  test_ctx.use_separate_verification = true;
  test_ctx.pass_app_signature_verification = true;
  test_ctx.pass_wik_signature_verification = true;

  grant_protocol_result_t res = grant_protocol_verify_grant(&grant);
  cr_assert_eq(res, GRANT_RESULT_OK);

  // Clean up
  cr_assert_eq(grant_protocol_delete_outstanding_request(), GRANT_RESULT_OK);
}

Test(grant_protocol_tests, invalid_app_signature, .init = setup, .fini = fini) {
  const bool production = true;
  grant_protocol_init(production);

  // Provision app auth pubkey
  cr_assert(grant_storage_write_app_auth_pubkey(FAKE_APP_PUBKEY));

  grant_request_t req;
  asserted_grant_request(&req, ACTION_FINGERPRINT_RESET);

  grant_t grant;
  mock_server_sign_grant(&req, &grant, production);

  // Enable separate verification to test app signature independently
  test_ctx.use_separate_verification = true;
  test_ctx.pass_app_signature_verification = false;  // App sig should fail
  test_ctx.pass_wik_signature_verification = true;   // WIK sig would pass (but we won't get there)

  grant_protocol_result_t res = grant_protocol_verify_grant(&grant);
  cr_assert_eq(res, GRANT_RESULT_ERROR_APP_VERIFICATION);
}

Test(grant_protocol_tests, app_auth_pubkey_provisioning, .init = setup, .fini = fini) {
  // Test initial provisioning
  cr_assert_eq(grant_storage_app_auth_pubkey_exists(), false);

  grant_protocol_result_t res = grant_protocol_provision_app_auth_pubkey(FAKE_APP_PUBKEY);
  cr_assert_eq(res, GRANT_RESULT_OK);
  cr_assert(grant_storage_app_auth_pubkey_exists());

  uint8_t read_pubkey[33];
  cr_assert(grant_storage_read_app_auth_pubkey(read_pubkey));
  cr_util_cmp_buffers(read_pubkey, FAKE_APP_PUBKEY, 33);
}

Test(grant_protocol_tests, app_auth_pubkey_overwrite, .init = setup, .fini = fini) {
  // First provision a key
  grant_protocol_result_t res = grant_protocol_provision_app_auth_pubkey(FAKE_APP_PUBKEY);
  cr_assert_eq(res, GRANT_RESULT_OK);

  // Overwrite with a new key (no signature required anymore)
  uint8_t new_pubkey[33] = {0x03};  // Different pubkey
  res = grant_protocol_provision_app_auth_pubkey(new_pubkey);
  cr_assert_eq(res, GRANT_RESULT_OK);

  // Verify new key is in place
  uint8_t read_pubkey[33];
  cr_assert(grant_storage_read_app_auth_pubkey(read_pubkey));
  cr_util_cmp_buffers(read_pubkey, new_pubkey, 33);
}
