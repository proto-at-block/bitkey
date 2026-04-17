#include "attributes.h"
#include "criterion_test_utils.h"
#include "ew.h"
#include "fff.h"
#include "lfs.h"
#include "mempool.h"
#include "rtos.h"
#include "secure_rng.h"
#include "wallet_address.h"

#include <criterion/criterion.h>

#include <string.h>

DEFINE_FFF_GLOBALS;
FAKE_VOID_FUNC(rtos_mutex_create, rtos_mutex_t*);

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

// Semaphore fakes
FAKE_VOID_FUNC(rtos_semaphore_create, rtos_semaphore_t*);
bool rtos_semaphore_give(rtos_semaphore_t* UNUSED(s)) {
  return true;
}
bool rtos_semaphore_give_from_isr(rtos_semaphore_t* UNUSED(s)) {
  return true;
}
bool rtos_semaphore_take(rtos_semaphore_t* UNUSED(s), uint32_t UNUSED(t)) {
  return true;
}
bool rtos_semaphore_take_from_isr(rtos_semaphore_t* UNUSED(s)) {
  return true;
}

// Thread fakes
typedef void (*f_cb)(void*);
FAKE_VOID_FUNC(rtos_thread_create_static, rtos_thread_t*, f_cb, const char*, void*,
               rtos_thread_priority_t, uint32_t*, uint32_t, StaticTask_t*, rtos_thread_mpu_t);
FAKE_VOID_FUNC(rtos_thread_delete, rtos_thread_t*);

// Event group fakes
FAKE_VOID_FUNC(rtos_event_group_create, rtos_event_group_t*);
FAKE_VALUE_FUNC(uint32_t, rtos_event_group_set_bits, rtos_event_group_t*, const uint32_t);
FAKE_VALUE_FUNC(bool, rtos_event_group_set_bits_from_isr, rtos_event_group_t*, const uint32_t,
                bool*);
FAKE_VALUE_FUNC(uint32_t, rtos_event_group_get_bits, rtos_event_group_t*);
FAKE_VALUE_FUNC(uint32_t, rtos_event_group_wait_bits, rtos_event_group_t*, const uint32_t,
                const bool, const bool, uint32_t);
FAKE_VALUE_FUNC(uint32_t, rtos_event_group_clear_bits, rtos_event_group_t*, const uint32_t);

// Block device fakes
FAKE_VALUE_FUNC(bool, bd_error_str, char*, const size_t, const int);
FAKE_VALUE_FUNC(int, bd_erase_all);
FAKE_VALUE_FUNC(lfs_t*, bd_mount);

static mempool_t* ew_pool;

static bool ew_random(uint8_t* out, size_t len) {
  return crypto_random(out, (uint32_t)len) ? 0 : 1;
}

static void ew_memzero(void* const pnt, const size_t len) {
  volatile uint8_t* vp = (volatile uint8_t*)pnt;
  for (size_t i = 0; i < len; i++) {
    vp[i] = 0;
  }
}

static void* ew_malloc(size_t size) {
  return ew_pool ? mempool_alloc(ew_pool, size) : NULL;
}

static void ew_free(void* ptr) {
  if (ew_pool && ptr) {
    mempool_free(ew_pool, ptr);
  }
}

void setup(void) {
#define REGIONS(X) X(ew_pool, addr, 96, 2)
  ew_pool = mempool_create(ew_pool);
#undef REGIONS

  ew_api_t api = {
    .crypto_random = ew_random,
    .secure_memzero = ew_memzero,
    .malloc = ew_malloc,
    .free = ew_free,
  };
  cr_assert_eq(ew_init(&api), EW_OK);
}

void teardown(void) {
  ew_cleanup();
}

// BIP32 test vectors
static const uint8_t TEST_APP_PUBKEY[33] = {0x03, 0x39, 0xa3, 0x60, 0x13, 0x30, 0x15, 0x97, 0xda,
                                            0xef, 0x41, 0xfb, 0xe5, 0x93, 0xa0, 0x2c, 0xc5, 0x13,
                                            0xd0, 0xb5, 0x55, 0x27, 0xec, 0x2d, 0xf1, 0x05, 0x0e,
                                            0x2e, 0x8f, 0xf4, 0x9c, 0x85, 0xc2};

static const uint8_t TEST_APP_CHAINCODE[32] = {
  0x87, 0x3d, 0xff, 0x81, 0xc0, 0x2f, 0x52, 0x56, 0x23, 0xfd, 0x1f, 0xe5, 0x16, 0x7e, 0xac, 0x3a,
  0x55, 0xa0, 0x49, 0xde, 0x3d, 0x31, 0x4b, 0xb4, 0x2e, 0xe2, 0x27, 0xff, 0xed, 0x37, 0xd5, 0x08};

// Server spending key (from BIP32 test vector 2)
static const uint8_t TEST_SERVER_PUBKEY[33] = {0x03, 0xcb, 0xca, 0xa9, 0xc9, 0x8c, 0x87, 0x7a, 0x26,
                                               0x97, 0x7d, 0x00, 0x82, 0x5c, 0x95, 0x6a, 0x23, 0x8e,
                                               0x8d, 0xdd, 0xfb, 0xd3, 0x22, 0xcc, 0xe4, 0xf7, 0x4b,
                                               0x0b, 0x5b, 0xd6, 0xac, 0xe4, 0xa7};

static const uint8_t TEST_SERVER_CHAINCODE[32] = {
  0x60, 0x49, 0x9f, 0x80, 0x1b, 0x89, 0x6d, 0x83, 0x17, 0x9a, 0x43, 0x74, 0xae, 0xb7, 0x82, 0x2a,
  0xae, 0xac, 0xea, 0xa0, 0xdb, 0x1f, 0x85, 0xee, 0x3e, 0x90, 0x4c, 0x4d, 0xef, 0xbd, 0x96, 0x89};

// HW spending key (from BIP32 test vector 3)
static const uint8_t TEST_HW_PUBKEY[33] = {0x03, 0x77, 0x4a, 0xe7, 0xf8, 0x58, 0xa9, 0x41, 0x1e,
                                           0x5e, 0xf4, 0x24, 0x6b, 0x70, 0xc6, 0x5a, 0xac, 0x56,
                                           0x49, 0x98, 0x0b, 0xe5, 0xc1, 0x78, 0x91, 0xbb, 0xec,
                                           0x17, 0x89, 0x5d, 0xa0, 0x08, 0xcb};

static const uint8_t TEST_HW_CHAINCODE[32] = {
  0x46, 0x32, 0x23, 0xaa, 0xc1, 0x0f, 0xb1, 0x3f, 0x29, 0x1a, 0x1b, 0xc7, 0x6b, 0xc2, 0x60, 0x03,
  0xd9, 0x8d, 0xa6, 0x61, 0xcb, 0x76, 0xdf, 0x61, 0xe7, 0x50, 0xc1, 0x39, 0x82, 0x6d, 0xea, 0x8b};

Test(wallet_address, mainnet, .init = setup, .fini = teardown) {
  wallet_keyset_t desc = {
    .version = WALLET_KEYSET_VERSION,
    .network = NETWORK_MAINNET,
  };
  memcpy(desc.app.pubkey, TEST_APP_PUBKEY, 33);
  memcpy(desc.app.chaincode, TEST_APP_CHAINCODE, 32);
  memcpy(desc.server.pubkey, TEST_SERVER_PUBKEY, 33);
  memcpy(desc.server.chaincode, TEST_SERVER_CHAINCODE, 32);
  memcpy(desc.hw.pubkey, TEST_HW_PUBKEY, 33);
  memcpy(desc.hw.chaincode, TEST_HW_CHAINCODE, 32);

  char addr[128];
  cr_assert_eq(wallet_derive_address(&desc, 0, addr, sizeof(addr)), WALLET_RES_OK);
  cr_assert(strncmp(addr, "bc1", 3) == 0);
}

Test(wallet_address, testnet, .init = setup, .fini = teardown) {
  wallet_keyset_t desc = {
    .version = WALLET_KEYSET_VERSION,
    .network = NETWORK_TESTNET,
  };
  memcpy(desc.app.pubkey, TEST_APP_PUBKEY, 33);
  memcpy(desc.app.chaincode, TEST_APP_CHAINCODE, 32);
  memcpy(desc.server.pubkey, TEST_SERVER_PUBKEY, 33);
  memcpy(desc.server.chaincode, TEST_SERVER_CHAINCODE, 32);
  memcpy(desc.hw.pubkey, TEST_HW_PUBKEY, 33);
  memcpy(desc.hw.chaincode, TEST_HW_CHAINCODE, 32);

  char addr[128];
  cr_assert_eq(wallet_derive_address(&desc, 0, addr, sizeof(addr)), WALLET_RES_OK);
  cr_assert(strncmp(addr, "tb1", 3) == 0 || strncmp(addr, "bcrt1", 5) == 0);
}

Test(wallet_address, multiple_indices, .init = setup, .fini = teardown) {
  wallet_keyset_t desc = {.version = WALLET_KEYSET_VERSION, .network = NETWORK_MAINNET};
  memcpy(desc.app.pubkey, TEST_APP_PUBKEY, 33);
  memcpy(desc.app.chaincode, TEST_APP_CHAINCODE, 32);
  memcpy(desc.server.pubkey, TEST_SERVER_PUBKEY, 33);
  memcpy(desc.server.chaincode, TEST_SERVER_CHAINCODE, 32);
  memcpy(desc.hw.pubkey, TEST_HW_PUBKEY, 33);
  memcpy(desc.hw.chaincode, TEST_HW_CHAINCODE, 32);

  char addr0[128], addr1[128], addr10[128];
  cr_assert_eq(wallet_derive_address(&desc, 0, addr0, sizeof(addr0)), WALLET_RES_OK);
  cr_assert_eq(wallet_derive_address(&desc, 1, addr1, sizeof(addr1)), WALLET_RES_OK);
  cr_assert_eq(wallet_derive_address(&desc, 10, addr10, sizeof(addr10)), WALLET_RES_OK);

  cr_assert_str_neq(addr0, addr1);
  cr_assert_str_neq(addr0, addr10);
  cr_assert_str_neq(addr1, addr10);
}

Test(wallet_address, deterministic, .init = setup, .fini = teardown) {
  wallet_keyset_t desc = {.version = WALLET_KEYSET_VERSION, .network = NETWORK_MAINNET};
  memcpy(desc.app.pubkey, TEST_APP_PUBKEY, 33);
  memcpy(desc.app.chaincode, TEST_APP_CHAINCODE, 32);
  memcpy(desc.server.pubkey, TEST_SERVER_PUBKEY, 33);
  memcpy(desc.server.chaincode, TEST_SERVER_CHAINCODE, 32);
  memcpy(desc.hw.pubkey, TEST_HW_PUBKEY, 33);
  memcpy(desc.hw.chaincode, TEST_HW_CHAINCODE, 32);

  char addr1[128], addr2[128];
  cr_assert_eq(wallet_derive_address(&desc, 5, addr1, sizeof(addr1)), WALLET_RES_OK);
  cr_assert_eq(wallet_derive_address(&desc, 5, addr2, sizeof(addr2)), WALLET_RES_OK);
  cr_assert_str_eq(addr1, addr2);
}

Test(wallet_address, null_descriptor) {
  char addr[128];
  cr_assert_eq(wallet_derive_address(NULL, 0, addr, sizeof(addr)), WALLET_RES_ERR);
}

Test(wallet_address, null_address_out, .init = setup, .fini = teardown) {
  wallet_keyset_t desc = {.version = WALLET_KEYSET_VERSION};
  memcpy(desc.app.pubkey, TEST_APP_PUBKEY, 33);
  cr_assert_eq(wallet_derive_address(&desc, 0, NULL, 128), WALLET_RES_ERR);
}

Test(wallet_address, zero_len, .init = setup, .fini = teardown) {
  wallet_keyset_t desc = {.version = WALLET_KEYSET_VERSION};
  char addr[128];
  cr_assert_eq(wallet_derive_address(&desc, 0, addr, 0), WALLET_RES_ERR);
}

Test(wallet_address, bad_version, .init = setup, .fini = teardown) {
  wallet_keyset_t desc = {.version = 99};
  char addr[128];
  cr_assert_eq(wallet_derive_address(&desc, 0, addr, sizeof(addr)), WALLET_RES_ERR);
}

Test(wallet_address, hardened_index, .init = setup, .fini = teardown) {
  wallet_keyset_t desc = {.version = WALLET_KEYSET_VERSION};
  memcpy(desc.app.pubkey, TEST_APP_PUBKEY, 33);
  char addr[128];
  cr_assert_eq(wallet_derive_address(&desc, 0x80000000, addr, sizeof(addr)), WALLET_RES_ERR);
}

// ---------------------------------------------------------------------------
// wallet_derive_p2wsh_scriptpubkey / wallet_change_output_belongs_to_policy tests
// ---------------------------------------------------------------------------

// Build a complete test keyset from the three test key pairs above.
static wallet_keyset_t make_test_keyset(void) {
  wallet_keyset_t keyset = {
    .version = WALLET_KEYSET_VERSION,
    .network = NETWORK_MAINNET,
  };
  memcpy(keyset.app.pubkey, TEST_APP_PUBKEY, 33);
  memcpy(keyset.app.chaincode, TEST_APP_CHAINCODE, 32);
  memcpy(keyset.server.pubkey, TEST_SERVER_PUBKEY, 33);
  memcpy(keyset.server.chaincode, TEST_SERVER_CHAINCODE, 32);
  memcpy(keyset.hw.pubkey, TEST_HW_PUBKEY, 33);
  memcpy(keyset.hw.chaincode, TEST_HW_CHAINCODE, 32);
  return keyset;
}

// Standard BIP84 path: m/84'/0'/0'/0/5  (receive chain, index=5)
static const uint32_t TEST_RECEIVE_PATH[] = {
  84 | 0x80000000u,  // 84'
  0 | 0x80000000u,   // 0' (mainnet)
  0 | 0x80000000u,   // 0' (account)
  0,                 // 0  (receive chain; 1 would be change)
  5,                 // 5  (address index)
};
static const size_t TEST_RECEIVE_PATH_LEN = 5;

Test(wallet_address, derive_p2wsh_scriptpubkey_ok, .init = setup, .fini = teardown) {
  wallet_keyset_t keyset = make_test_keyset();

  uint8_t spk[34] = {0};
  size_t spk_len = 0;
  wallet_res_t res = wallet_derive_p2wsh_scriptpubkey(
    &keyset, TEST_RECEIVE_PATH, TEST_RECEIVE_PATH_LEN, spk, sizeof(spk), &spk_len);
  cr_assert_eq(res, WALLET_RES_OK);
  // P2WSH scriptPubKey is always 34 bytes: OP_0 <32-byte SHA256 hash>
  cr_assert_eq(spk_len, 34u);
  cr_assert_eq(spk[0], 0x00);  // OP_0
  cr_assert_eq(spk[1], 0x20);  // push 32 bytes
}

Test(wallet_address, derive_p2wsh_scriptpubkey_deterministic, .init = setup, .fini = teardown) {
  wallet_keyset_t keyset = make_test_keyset();

  uint8_t spk1[34] = {0}, spk2[34] = {0};
  size_t len1 = 0, len2 = 0;
  cr_assert_eq(wallet_derive_p2wsh_scriptpubkey(&keyset, TEST_RECEIVE_PATH, TEST_RECEIVE_PATH_LEN,
                                                spk1, sizeof(spk1), &len1),
               WALLET_RES_OK);
  cr_assert_eq(wallet_derive_p2wsh_scriptpubkey(&keyset, TEST_RECEIVE_PATH, TEST_RECEIVE_PATH_LEN,
                                                spk2, sizeof(spk2), &len2),
               WALLET_RES_OK);
  cr_assert_eq(len1, len2);
  cr_assert_arr_eq(spk1, spk2, len1);
}

Test(wallet_address, derive_p2wsh_scriptpubkey_differs_per_index, .init = setup, .fini = teardown) {
  wallet_keyset_t keyset = make_test_keyset();

  const uint32_t path_idx1[] = {84 | 0x80000000u, 0 | 0x80000000u, 0 | 0x80000000u, 0, 1};
  const uint32_t path_idx2[] = {84 | 0x80000000u, 0 | 0x80000000u, 0 | 0x80000000u, 0, 2};

  uint8_t spk1[34] = {0}, spk2[34] = {0};
  size_t len1 = 0, len2 = 0;
  cr_assert_eq(wallet_derive_p2wsh_scriptpubkey(&keyset, path_idx1, 5, spk1, sizeof(spk1), &len1),
               WALLET_RES_OK);
  cr_assert_eq(wallet_derive_p2wsh_scriptpubkey(&keyset, path_idx2, 5, spk2, sizeof(spk2), &len2),
               WALLET_RES_OK);
  cr_assert_eq(len1, len2);
  // Different indices must produce different scriptPubKeys
  cr_assert(memcmp(spk1, spk2, len1) != 0, "Different indices should produce different SPKs");
}

Test(wallet_address, derive_p2wsh_scriptpubkey_null_keyset, .init = setup, .fini = teardown) {
  uint8_t spk[34] = {0};
  size_t spk_len = 0;
  cr_assert_eq(wallet_derive_p2wsh_scriptpubkey(NULL, TEST_RECEIVE_PATH, TEST_RECEIVE_PATH_LEN, spk,
                                                sizeof(spk), &spk_len),
               WALLET_RES_ERR);
}

Test(wallet_address, derive_p2wsh_scriptpubkey_bad_version, .init = setup, .fini = teardown) {
  wallet_keyset_t keyset = make_test_keyset();
  keyset.version = 99;
  uint8_t spk[34] = {0};
  size_t spk_len = 0;
  cr_assert_eq(wallet_derive_p2wsh_scriptpubkey(&keyset, TEST_RECEIVE_PATH, TEST_RECEIVE_PATH_LEN,
                                                spk, sizeof(spk), &spk_len),
               WALLET_RES_ERR);
}

Test(wallet_address, derive_p2wsh_scriptpubkey_zero_path_len, .init = setup, .fini = teardown) {
  wallet_keyset_t keyset = make_test_keyset();
  uint8_t spk[34] = {0};
  size_t spk_len = 0;
  cr_assert_eq(
    wallet_derive_p2wsh_scriptpubkey(&keyset, TEST_RECEIVE_PATH, 0, spk, sizeof(spk), &spk_len),
    WALLET_RES_ERR);
}

// Path too short: only 3 components (account depth, no change/index levels).
Test(wallet_address, derive_p2wsh_scriptpubkey_path_too_short, .init = setup, .fini = teardown) {
  wallet_keyset_t keyset = make_test_keyset();
  const uint32_t short_path[] = {84 | 0x80000000u, 0 | 0x80000000u, 0 | 0x80000000u};
  uint8_t spk[34] = {0};
  size_t spk_len = 0;
  cr_assert_eq(wallet_derive_p2wsh_scriptpubkey(&keyset, short_path, 3, spk, sizeof(spk), &spk_len),
               WALLET_RES_ERR);
}

// Path with 4 components (missing address index) must be rejected.
// A malicious app could use a 4-level path to create a UTXO that can't be spent
// through the normal 5-level signing flow, effectively trapping funds.
Test(wallet_address, derive_p2wsh_scriptpubkey_path_4_components, .init = setup, .fini = teardown) {
  wallet_keyset_t keyset = make_test_keyset();
  const uint32_t path_4[] = {84 | 0x80000000u, 0 | 0x80000000u, 0 | 0x80000000u, 1};
  uint8_t spk[34] = {0};
  size_t spk_len = 0;
  cr_assert_eq(wallet_derive_p2wsh_scriptpubkey(&keyset, path_4, 4, spk, sizeof(spk), &spk_len),
               WALLET_RES_ERR);
}

// Wrong BIP84 purpose (48' instead of 84') must be rejected.
Test(wallet_address, derive_p2wsh_scriptpubkey_wrong_purpose, .init = setup, .fini = teardown) {
  wallet_keyset_t keyset = make_test_keyset();
  const uint32_t path[] = {48 | 0x80000000u, 0 | 0x80000000u, 0 | 0x80000000u, 1, 0};
  uint8_t spk[34] = {0};
  size_t spk_len = 0;
  cr_assert_eq(wallet_derive_p2wsh_scriptpubkey(&keyset, path, 5, spk, sizeof(spk), &spk_len),
               WALLET_RES_ERR);
}

// Wrong coin type (testnet coin on mainnet keyset) must be rejected.
Test(wallet_address, derive_p2wsh_scriptpubkey_wrong_coin_type, .init = setup, .fini = teardown) {
  wallet_keyset_t keyset = make_test_keyset();  // mainnet
  const uint32_t path[] = {84 | 0x80000000u, 1 | 0x80000000u, 0 | 0x80000000u, 1, 0};
  uint8_t spk[34] = {0};
  size_t spk_len = 0;
  cr_assert_eq(wallet_derive_p2wsh_scriptpubkey(&keyset, path, 5, spk, sizeof(spk), &spk_len),
               WALLET_RES_ERR);
}

// Unhardened account index must be rejected.
Test(wallet_address, derive_p2wsh_scriptpubkey_unhardened_account, .init = setup,
     .fini = teardown) {
  wallet_keyset_t keyset = make_test_keyset();
  const uint32_t path[] = {84 | 0x80000000u, 0 | 0x80000000u, 0 /* not hardened */, 1, 0};
  uint8_t spk[34] = {0};
  size_t spk_len = 0;
  cr_assert_eq(wallet_derive_p2wsh_scriptpubkey(&keyset, path, 5, spk, sizeof(spk), &spk_len),
               WALLET_RES_ERR);
}

// Change level > 1 must be rejected.
Test(wallet_address, derive_p2wsh_scriptpubkey_bad_change_level, .init = setup, .fini = teardown) {
  wallet_keyset_t keyset = make_test_keyset();
  const uint32_t path[] = {84 | 0x80000000u, 0 | 0x80000000u, 0 | 0x80000000u, 2, 0};
  uint8_t spk[34] = {0};
  size_t spk_len = 0;
  cr_assert_eq(wallet_derive_p2wsh_scriptpubkey(&keyset, path, 5, spk, sizeof(spk), &spk_len),
               WALLET_RES_ERR);
}

Test(wallet_address, change_output_belongs_to_policy_valid, .init = setup, .fini = teardown) {
  wallet_keyset_t keyset = make_test_keyset();

  // Derive the expected scriptPubKey, then validate it passes the policy check.
  uint8_t expected_spk[34] = {0};
  size_t expected_spk_len = 0;
  cr_assert_eq(
    wallet_derive_p2wsh_scriptpubkey(&keyset, TEST_RECEIVE_PATH, TEST_RECEIVE_PATH_LEN,
                                     expected_spk, sizeof(expected_spk), &expected_spk_len),
    WALLET_RES_OK);

  cr_assert(wallet_change_output_belongs_to_policy(
              &keyset, TEST_RECEIVE_PATH, TEST_RECEIVE_PATH_LEN, expected_spk, expected_spk_len),
            "Correctly derived SPK should pass policy check");
}

Test(wallet_address, change_output_belongs_to_policy_wrong_spk, .init = setup, .fini = teardown) {
  wallet_keyset_t keyset = make_test_keyset();

  uint8_t expected_spk[34] = {0};
  size_t expected_spk_len = 0;
  cr_assert_eq(
    wallet_derive_p2wsh_scriptpubkey(&keyset, TEST_RECEIVE_PATH, TEST_RECEIVE_PATH_LEN,
                                     expected_spk, sizeof(expected_spk), &expected_spk_len),
    WALLET_RES_OK);

  // Flip one byte to simulate an attacker-controlled scriptPubKey.
  expected_spk[expected_spk_len - 1] ^= 0x01;

  cr_assert_not(
    wallet_change_output_belongs_to_policy(&keyset, TEST_RECEIVE_PATH, TEST_RECEIVE_PATH_LEN,
                                           expected_spk, expected_spk_len),
    "Tampered SPK should fail policy check");
}

Test(wallet_address, change_output_belongs_to_policy_wrong_path, .init = setup, .fini = teardown) {
  wallet_keyset_t keyset = make_test_keyset();

  // Derive SPK for path A.
  const uint32_t path_a[] = {84 | 0x80000000u, 0 | 0x80000000u, 0 | 0x80000000u, 0, 3};
  uint8_t spk_a[34] = {0};
  size_t spk_a_len = 0;
  cr_assert_eq(
    wallet_derive_p2wsh_scriptpubkey(&keyset, path_a, 5, spk_a, sizeof(spk_a), &spk_a_len),
    WALLET_RES_OK);

  // Validate with path B (different index) - must fail.
  const uint32_t path_b[] = {84 | 0x80000000u, 0 | 0x80000000u, 0 | 0x80000000u, 0, 7};
  cr_assert_not(wallet_change_output_belongs_to_policy(&keyset, path_b, 5, spk_a, spk_a_len),
                "SPK from path A must not validate against path B");
}

Test(wallet_address, change_output_belongs_to_policy_null_inputs, .init = setup, .fini = teardown) {
  wallet_keyset_t keyset = make_test_keyset();
  uint8_t dummy_spk[34] = {0x00, 0x20};

  cr_assert_not(wallet_change_output_belongs_to_policy(
    NULL, TEST_RECEIVE_PATH, TEST_RECEIVE_PATH_LEN, dummy_spk, sizeof(dummy_spk)));
  cr_assert_not(wallet_change_output_belongs_to_policy(&keyset, NULL, TEST_RECEIVE_PATH_LEN,
                                                       dummy_spk, sizeof(dummy_spk)));
  cr_assert_not(wallet_change_output_belongs_to_policy(
    &keyset, TEST_RECEIVE_PATH, TEST_RECEIVE_PATH_LEN, NULL, sizeof(dummy_spk)));
  cr_assert_not(wallet_change_output_belongs_to_policy(&keyset, TEST_RECEIVE_PATH,
                                                       TEST_RECEIVE_PATH_LEN, dummy_spk, 0));
}

// wallet_derive_address must produce the same result as building the full BIP84 receive path
// and calling wallet_derive_p2wsh_scriptpubkey + ew_script_to_address. This catches the bug
// where wallet_derive_address derived parent/index instead of parent/0/index (missing the
// external chain derivation step).
//
// Additionally, index 0 is checked against a hardcoded expected address that was independently
// derived using the BIP32 test vector keys and the same 2-of-3 P2WSH construction
// (embit library, Python). This cross-platform canary ensures firmware and app agree on the
// derivation — if both firmware code paths were wrong in the same way, this assertion would
// catch it.
Test(wallet_address, derive_address_matches_p2wsh_scriptpubkey, .init = setup, .fini = teardown) {
  wallet_keyset_t keyset = make_test_keyset();

  // Cross-platform canary: expected address at index 0, independently derived from the same
  // test keys using BIP32 public derivation + 2-of-3 sorted multisig P2WSH + bech32 encoding.
  // Derivation details (mainnet, account depth 3 for app/hw, depth 0 for server):
  //   App:    <TEST_APP_KEY>/0/0      → 02756de1...
  //   HW:     <TEST_HW_KEY>/0/0       → 035db1de...
  //   Server: <TEST_SERVER_KEY>/84/0/0/0/0 → 03848fe3...
  //   Sorted multisig witness script → SHA256 → P2WSH bech32
  static const char* EXPECTED_ADDR_INDEX_0 =
    "bc1qqadmw5ptldpflkwtz3n8lzqgr3za50hkxxjtvplahe68txzw55vs0jw4kk";

  for (uint32_t idx = 0; idx < 5; idx++) {
    // 1. Get address from wallet_derive_address
    char addr_from_fn[128] = {0};
    cr_assert_eq(wallet_derive_address(&keyset, idx, addr_from_fn, sizeof(addr_from_fn)),
                 WALLET_RES_OK);

    // 2. Derive scriptpubkey from the full BIP84 receive path
    const uint32_t path[] = {84 | 0x80000000u, 0 | 0x80000000u, 0 | 0x80000000u, 0, idx};
    uint8_t spk[34] = {0};
    size_t spk_len = 0;
    cr_assert_eq(wallet_derive_p2wsh_scriptpubkey(&keyset, path, 5, spk, sizeof(spk), &spk_len),
                 WALLET_RES_OK);

    // 3. Convert scriptpubkey to bech32 address
    char addr_from_spk[128] = {0};
    cr_assert_eq(
      ew_script_to_address(spk, spk_len, EW_NETWORK_MAINNET, addr_from_spk, sizeof(addr_from_spk)),
      EW_OK);

    // 4. Both paths must produce the same address
    cr_assert_str_eq(addr_from_fn, addr_from_spk,
                     "wallet_derive_address(idx=%u) must match BIP84 receive path derivation", idx);

    // 5. Cross-platform canary: index 0 must match the independently derived expected address
    if (idx == 0) {
      cr_assert_str_eq(addr_from_fn, EXPECTED_ADDR_INDEX_0,
                       "Index 0 address must match independently derived BDK canary value");
    }
  }
}

// Account mismatch (keyset has account 0, path has account 1) must be rejected.
// This prevents "chimeric" scriptPubKeys where app/hw keys are derived from one account
// but server key from another, which could hide an unspendable output.
Test(wallet_address, derive_p2wsh_scriptpubkey_account_mismatch, .init = setup, .fini = teardown) {
  wallet_keyset_t keyset = make_test_keyset();  // account_index defaults to 0
  const uint32_t path_acct1[] = {84 | 0x80000000u, 0 | 0x80000000u, 1 | 0x80000000u, 0, 0};
  uint8_t spk[34] = {0};
  size_t spk_len = 0;
  cr_assert_eq(wallet_derive_p2wsh_scriptpubkey(&keyset, path_acct1, 5, spk, sizeof(spk), &spk_len),
               WALLET_RES_ERR, "Path account 1 must be rejected when keyset has account 0");
}

// Hardened address index (path[4] with HARDENED_BIT set) must be rejected early.
// bip32_derive_path_pub rejects hardened child derivation, but we validate explicitly
// to give a clear error rather than a silent downstream failure.
Test(wallet_address, derive_p2wsh_scriptpubkey_hardened_address_index, .init = setup,
     .fini = teardown) {
  wallet_keyset_t keyset = make_test_keyset();
  const uint32_t path[] = {84 | 0x80000000u, 0 | 0x80000000u, 0 | 0x80000000u, 0,
                           5 | 0x80000000u /* hardened index */};
  uint8_t spk[34] = {0};
  size_t spk_len = 0;
  cr_assert_eq(wallet_derive_p2wsh_scriptpubkey(&keyset, path, 5, spk, sizeof(spk), &spk_len),
               WALLET_RES_ERR, "Hardened address index must be rejected");
}

// Account mismatch with account=2 in path (keyset has account 0).
Test(wallet_address, derive_p2wsh_scriptpubkey_account_2_mismatch, .init = setup,
     .fini = teardown) {
  wallet_keyset_t keyset = make_test_keyset();  // account_index defaults to 0
  const uint32_t path_acct2[] = {84 | 0x80000000u, 0 | 0x80000000u, 2 | 0x80000000u, 1, 0};
  uint8_t spk[34] = {0};
  size_t spk_len = 0;
  cr_assert_eq(wallet_derive_p2wsh_scriptpubkey(&keyset, path_acct2, 5, spk, sizeof(spk), &spk_len),
               WALLET_RES_ERR, "Path account 2 must be rejected when keyset has account 0");
}

// Non-zero account succeeds when keyset account_index matches the path.
// This is the Lost App Recovery scenario where account is incremented.
Test(wallet_address, derive_p2wsh_scriptpubkey_nonzero_account_matching, .init = setup,
     .fini = teardown) {
  wallet_keyset_t keyset = make_test_keyset();
  keyset.account_index = 1;  // Set keyset to account 1
  const uint32_t path_acct1[] = {84 | 0x80000000u, 0 | 0x80000000u, 1 | 0x80000000u, 0, 0};
  uint8_t spk[34] = {0};
  size_t spk_len = 0;
  cr_assert_eq(wallet_derive_p2wsh_scriptpubkey(&keyset, path_acct1, 5, spk, sizeof(spk), &spk_len),
               WALLET_RES_OK, "Path account 1 must succeed when keyset has account 1");
  cr_assert_eq(spk_len, 34u);
}

// Server key always derives through account 0, independent of the HW account_index.
// This is the Lost App & Cloud recovery scenario: HW account increments to 1 but server
// stays at account 0. Before the fix, firmware inherited the HW account for the server
// path, causing "policy mismatch" errors.
//
// Note: in a real recovery the server issues an entirely new key & chaincode (not just a
// different account index). This test uses the same key material for both keysets to
// isolate the derivation-path logic from the key-material difference.
Test(wallet_address, derive_p2wsh_server_always_account_zero, .init = setup, .fini = teardown) {
  // Keyset with HW at account 1 (post-recovery). Path must match account_index.
  wallet_keyset_t keyset1 = make_test_keyset();
  keyset1.account_index = 1;
  const uint32_t path1[] = {84 | 0x80000000u, 0 | 0x80000000u, 1 | 0x80000000u, 0, 0};
  uint8_t spk1[34] = {0};
  size_t spk1_len = 0;
  cr_assert_eq(wallet_derive_p2wsh_scriptpubkey(&keyset1, path1, 5, spk1, sizeof(spk1), &spk1_len),
               WALLET_RES_OK);
  cr_assert_eq(spk1_len, 34u);

  // Keyset with HW at account 0 (normal onboarding). Same key material.
  wallet_keyset_t keyset0 = make_test_keyset();
  const uint32_t path0[] = {84 | 0x80000000u, 0 | 0x80000000u, 0 | 0x80000000u, 0, 0};
  uint8_t spk0[34] = {0};
  size_t spk0_len = 0;
  cr_assert_eq(wallet_derive_p2wsh_scriptpubkey(&keyset0, path0, 5, spk0, sizeof(spk0), &spk0_len),
               WALLET_RES_OK);

  // With the same key material, both must produce identical scriptPubKeys because:
  // - App/HW xpubs are at depth 3: derivation starts at change/index, skipping account
  // - Server path always uses account 0, regardless of keyset.account_index
  // Before the fix, the account=1 keyset would derive the server through account 1,
  // producing a different (wrong) scriptPubKey.
  cr_assert_eq(spk1_len, spk0_len);
  cr_assert_arr_eq(spk1, spk0, spk1_len,
                   "Same key material with different account_index must produce identical SPKs");
}

// wallet_derive_address with nonzero account must be consistent with
// wallet_derive_p2wsh_scriptpubkey — proving signing paths agree with address derivation.
Test(wallet_address, derive_address_nonzero_account_consistency, .init = setup, .fini = teardown) {
  wallet_keyset_t keyset = make_test_keyset();
  keyset.account_index = 1;  // Recovery scenario

  for (uint32_t idx = 0; idx < 3; idx++) {
    char addr[128] = {0};
    cr_assert_eq(wallet_derive_address(&keyset, idx, addr, sizeof(addr)), WALLET_RES_OK);

    const uint32_t path[] = {84 | 0x80000000u, 0 | 0x80000000u, 1 | 0x80000000u, 0, idx};
    uint8_t spk[34] = {0};
    size_t spk_len = 0;
    cr_assert_eq(wallet_derive_p2wsh_scriptpubkey(&keyset, path, 5, spk, sizeof(spk), &spk_len),
                 WALLET_RES_OK);

    char addr_from_spk[128] = {0};
    cr_assert_eq(
      ew_script_to_address(spk, spk_len, EW_NETWORK_MAINNET, addr_from_spk, sizeof(addr_from_spk)),
      EW_OK);

    cr_assert_str_eq(addr, addr_from_spk,
                     "wallet_derive_address(idx=%u) must match p2wsh derivation at account 1", idx);
  }
}

// wallet_derive_address must use keyset->account_index when building the BIP84 path.
Test(wallet_address, derive_address_nonzero_account, .init = setup, .fini = teardown) {
  wallet_keyset_t keyset = make_test_keyset();
  keyset.account_index = 1;  // Non-zero account

  // Get address from wallet_derive_address
  char addr[128] = {0};
  cr_assert_eq(wallet_derive_address(&keyset, 0, addr, sizeof(addr)), WALLET_RES_OK);

  // Derive the same address via the full BIP84 path with account=1
  const uint32_t path[] = {84 | 0x80000000u, 0 | 0x80000000u, 1 | 0x80000000u, 0, 0};
  uint8_t spk[34] = {0};
  size_t spk_len = 0;
  cr_assert_eq(wallet_derive_p2wsh_scriptpubkey(&keyset, path, 5, spk, sizeof(spk), &spk_len),
               WALLET_RES_OK);

  // Convert scriptpubkey to address
  char addr_from_spk[128] = {0};
  cr_assert_eq(
    ew_script_to_address(spk, spk_len, EW_NETWORK_MAINNET, addr_from_spk, sizeof(addr_from_spk)),
    EW_OK);

  // Both methods must produce the same address
  cr_assert_str_eq(addr, addr_from_spk, "wallet_derive_address must use keyset account_index");
}

Test(wallet_address, change_output_belongs_to_policy_change_index_1, .init = setup,
     .fini = teardown) {
  wallet_keyset_t keyset = make_test_keyset();

  // Change output (change=1) - verify it produces a distinct scriptPubKey from receive (change=0).
  const uint32_t path_change[] = {84 | 0x80000000u, 0 | 0x80000000u, 0 | 0x80000000u, 1, 0};
  const uint32_t path_receive[] = {84 | 0x80000000u, 0 | 0x80000000u, 0 | 0x80000000u, 0, 0};

  uint8_t spk_change[34] = {0}, spk_receive[34] = {0};
  size_t len_change = 0, len_receive = 0;

  cr_assert_eq(wallet_derive_p2wsh_scriptpubkey(&keyset, path_change, 5, spk_change,
                                                sizeof(spk_change), &len_change),
               WALLET_RES_OK);
  cr_assert_eq(wallet_derive_p2wsh_scriptpubkey(&keyset, path_receive, 5, spk_receive,
                                                sizeof(spk_receive), &len_receive),
               WALLET_RES_OK);

  cr_assert(memcmp(spk_change, spk_receive, len_change) != 0,
            "Change and receive SPKs at index 0 must differ");

  // Each must validate against its own path but not the other's.
  cr_assert(
    wallet_change_output_belongs_to_policy(&keyset, path_change, 5, spk_change, len_change));
  cr_assert(
    wallet_change_output_belongs_to_policy(&keyset, path_receive, 5, spk_receive, len_receive));
  cr_assert_not(
    wallet_change_output_belongs_to_policy(&keyset, path_change, 5, spk_receive, len_receive));
  cr_assert_not(
    wallet_change_output_belongs_to_policy(&keyset, path_receive, 5, spk_change, len_change));
}
