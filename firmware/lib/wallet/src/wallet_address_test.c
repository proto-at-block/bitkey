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
