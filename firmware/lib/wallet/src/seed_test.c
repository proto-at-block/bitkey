#include "bd/lfs_emubd.h"
#include "criterion_test_utils.h"
#include "fff.h"
#include "filesystem.h"
#include "rtos.h"
#include "seed_impl.h"
#include "wallet_impl.h"
#include "wkek_impl.h"

#include <criterion/criterion.h>
#include <criterion/new/assert.h>

DEFINE_FFF_GLOBALS;
FAKE_VOID_FUNC(rtos_mutex_create, rtos_mutex_t*);
FAKE_VALUE_FUNC(bool, crypto_hkdf, key_handle_t*, hash_alg_t, uint8_t*, size_t, uint8_t*, size_t,
                key_handle_t*);
FAKE_VALUE_FUNC(bool, export_pubkey, key_handle_t*, key_handle_t*);

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

FAKE_VOID_FUNC(_putchar, char);

FAKE_VOID_FUNC(rtos_semaphore_create, rtos_semaphore_t*);

typedef void (*f_cb)(void*);
FAKE_VOID_FUNC(rtos_thread_create_static, rtos_thread_t*, f_cb, const char*, void*,
               rtos_thread_priority_t, uint32_t*, uint32_t, StaticTask_t*, rtos_thread_mpu_t);
FAKE_VOID_FUNC(rtos_thread_delete, rtos_thread_t*);

FAKE_VALUE_FUNC(uint64_t, rtos_thread_micros);

FAKE_VOID_FUNC(rtos_event_group_create, rtos_event_group_t*);
FAKE_VALUE_FUNC(uint32_t, rtos_event_group_set_bits, rtos_event_group_t*, const uint32_t);
FAKE_VALUE_FUNC(uint32_t, rtos_event_group_wait_bits, rtos_event_group_t*, const uint32_t,
                const bool, const bool, uint32_t);
FAKE_VALUE_FUNC(uint32_t, rtos_event_group_clear_bits, rtos_event_group_t*, const uint32_t);

FAKE_VALUE_FUNC(bool, bd_error_str, char*, const size_t, const int);
FAKE_VALUE_FUNC(int, bd_erase_all);
FAKE_VALUE_FUNC(lfs_t*, bd_mount);

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

static mempool_t* mempool;

// BIP32 Test vector 4
// https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki#user-content-Test_vector_4
static uint8_t expected_seed[] = {0x3d, 0xdd, 0x56, 0x02, 0x28, 0x58, 0x99, 0xa9, 0x46, 0x11, 0x45,
                                  0x06, 0x15, 0x7c, 0x79, 0x97, 0xe5, 0x44, 0x45, 0x28, 0xf3, 0x00,
                                  0x3f, 0x61, 0x34, 0x71, 0x21, 0x47, 0xdb, 0x19, 0xb6, 0x78};
static uint8_t expected_master_fingerprint[] = {0xad, 0x85, 0xd9, 0x55};

static void init_lfs() {
  cr_assert(lfs_emubd_create(&cfg, &emubd_cfg) == 0);
  cr_assert(lfs_format(&lfs, &cfg) == 0);
  cr_assert(lfs_mount(&lfs, &cfg) == 0);
  set_lfs(&lfs);
}

static void init_wkek() {
  init_lfs();
  cr_assert(wkek_exists() == false);
  cr_assert(wkek_lazy_init() == true);
  cr_assert(wkek_exists() == true);
}

static void init_wallet() {
  init_wkek();

#define REGIONS(X)                                                       \
  X(wallet_pool, extended_keys, WALLET_POOL_R0_SIZE, WALLET_POOL_R0_NUM) \
  X(wallet_pool, r1, WALLET_POOL_R1_SIZE, WALLET_POOL_R1_NUM)
  mempool = mempool_create(wallet_pool);
#undef REGIONS
  wallet_init(mempool);
}

void init_seed() {
  init_wallet();
  cr_assert(wkek_encrypt_and_store(SEED_PATH, expected_seed, sizeof(expected_seed)));
}

void init() {
  init_seed();
}

void fini() {
  lfs_emubd_destroy(&cfg);
}

Test(seed, seed_roundtrip, .init = init, .fini = fini) {
  // Seed is encrypted and stored in init_seed()
  uint8_t actual_seed[SEED_SIZE] = {0};
  cr_assert(wkek_read_and_decrypt(SEED_PATH, actual_seed, sizeof(actual_seed)));
  cr_util_cmp_buffers(actual_seed, expected_seed, sizeof(expected_seed));
}

Test(seed, derive_m, .init = init, .fini = fini) {
  extended_key_t actual_key = {0};
  fingerprint_t actual_master_fingerprint = {0};
  fingerprint_t actual_childs_parent_fingerprint = {0};

  const derivation_path_t path = {
    .indices = NULL,
    .num_indices = 0,
  };
  cr_assert_eq(seed_derive_bip32(path, &actual_key, &actual_master_fingerprint,
                                 &actual_childs_parent_fingerprint),
               SEED_RES_OK);

  uint8_t expected_key[BIP32_KEY_SIZE] = {
    0x12, 0xc0, 0xd5, 0x9c, 0x7a, 0xa3, 0xa1, 0x09, 0x73, 0xdb, 0xd3, 0xf4, 0x78, 0xb6, 0x5f, 0x25,
    0x16, 0x62, 0x7e, 0x3f, 0xe6, 0x1e, 0x00, 0xc3, 0x45, 0xbe, 0x9a, 0x47, 0x7a, 0xd2, 0xe2, 0x15};
  uint8_t expected_chaincode[BIP32_CHAINCODE_SIZE] = {
    0xd0, 0xc8, 0xa1, 0xf6, 0xed, 0xf2, 0x50, 0x07, 0x98, 0xc3, 0xe0, 0xb5, 0x4f, 0x1b, 0x56, 0xe4,
    0x5f, 0x6d, 0x03, 0xe6, 0x07, 0x6a, 0xbd, 0x36, 0xe5, 0xe2, 0xf5, 0x41, 0x01, 0xe4, 0x4c, 0xe6};
  uint8_t expected_childs_parent_fingerprint[BIP32_KEY_FINGERPRINT_SIZE] = {0, 0, 0, 0};

  cr_assert_eq(actual_key.prefix, BIP32_PRIVKEY_PREFIX);
  cr_util_cmp_buffers(actual_key.key, expected_key, sizeof(expected_key));
  cr_util_cmp_buffers(actual_key.chaincode, expected_chaincode, sizeof(expected_chaincode));
  cr_util_cmp_buffers(actual_master_fingerprint.bytes, expected_master_fingerprint,
                      sizeof(expected_master_fingerprint));
  cr_util_cmp_buffers(actual_childs_parent_fingerprint.bytes, expected_childs_parent_fingerprint,
                      sizeof(expected_childs_parent_fingerprint));
}

Test(seed, derive_m_0h_1h, .init = init, .fini = fini) {
  extended_key_t actual_key = {0};
  fingerprint_t actual_master_fingerprint = {0};
  fingerprint_t actual_childs_parent_fingerprint = {0};

  uint32_t indices[] = {0 | BIP32_HARDENED_BIT, 1 | BIP32_HARDENED_BIT};
  const derivation_path_t path = {
    .indices = indices,
    .num_indices = ARRAY_SIZE(indices),
  };

  cr_assert_eq(seed_derive_bip32(path, &actual_key, &actual_master_fingerprint,
                                 &actual_childs_parent_fingerprint),
               SEED_RES_OK);

  uint8_t expected_key[BIP32_KEY_SIZE] = {
    0x3a, 0x20, 0x86, 0xed, 0xd7, 0xd9, 0xdf, 0x86, 0xc3, 0x48, 0x7a, 0x59, 0x05, 0xa1, 0x71, 0x2a,
    0x9a, 0xa6, 0x64, 0xbc, 0xe8, 0xcc, 0x26, 0x81, 0x41, 0xe0, 0x75, 0x49, 0xea, 0xa8, 0x66, 0x1d};
  uint8_t expected_chaincode[BIP32_CHAINCODE_SIZE] = {
    0xa4, 0x8e, 0xe6, 0x67, 0x4c, 0x52, 0x64, 0xa2, 0x37, 0x70, 0x3f, 0xd3, 0x83, 0xbc, 0xcd, 0x9f,
    0xad, 0x4d, 0x93, 0x78, 0xac, 0x98, 0xab, 0x05, 0xe6, 0xe7, 0x02, 0x9b, 0x06, 0x36, 0x0c, 0x0d};
  uint8_t expected_childs_parent_fingerprint[BIP32_KEY_FINGERPRINT_SIZE] = {0xcf, 0xa6, 0x12, 0x81};

  cr_assert_eq(actual_key.prefix, BIP32_PRIVKEY_PREFIX);
  cr_util_cmp_buffers(actual_key.key, expected_key, sizeof(expected_key));
  cr_util_cmp_buffers(actual_key.chaincode, expected_chaincode, sizeof(expected_chaincode));
  cr_util_cmp_buffers(actual_master_fingerprint.bytes, expected_master_fingerprint,
                      sizeof(expected_master_fingerprint));
  cr_util_cmp_buffers(actual_childs_parent_fingerprint.bytes, expected_childs_parent_fingerprint,
                      sizeof(expected_childs_parent_fingerprint));
}
