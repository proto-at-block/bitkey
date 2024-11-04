#include "attributes.h"
#include "bd/lfs_emubd.h"
#include "bitlog.h"
#include "criterion_test_utils.h"
#include "derived_key_cache_impl.h"
#include "fff.h"
#include "filesystem.h"
#include "key_management.h"
#include "lfs.h"
#include "mempool.h"
#include "rtos.h"
#include "seed.h"
#include "wallet.h"

#include <criterion/criterion.h>
#include <criterion/new/assert.h>

#include <stdio.h>

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
static mempool_t* mempool;

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

extern derived_key_cache_t derived_key_cache;
extern bool derived_key_cache_initialized;

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

uint32_t timestamp(void) {
  return 0;
}

void detect_glitch(void) {}

uint16_t crypto_rand_short(void) {
  return 1;
}

uint32_t clock_get_freq(void) {
  return 1;
}

void setup(void) {
#define REGIONS(X) X(wallet_pool, derived_key_cache, 256, 3)
  mempool = mempool_create(wallet_pool);
#undef REGIONS
  wallet_init(mempool);
  bitlog_init((bitlog_api_t){
    .timestamp_cb = timestamp,
  });

  secutils_init((secutils_api_t){
    .detect_glitch = &detect_glitch,
    .secure_random = &crypto_rand_short,
    .cpu_freq = &clock_get_freq,
  });

  cr_assert(lfs_emubd_create(&cfg, &emubd_cfg) == 0);
  cr_assert(lfs_format(&lfs, &cfg) == 0);
  cr_assert(lfs_mount(&lfs, &cfg) == 0);
  set_lfs(&lfs);
}

void teardown(void) {
  lfs_emubd_destroy(&cfg);
}

static extended_key_t zero_key = {0};

static bool is_zero_key(extended_key_t* key) {
  return memcmp(key, &zero_key, sizeof(extended_key_t)) == 0;
}

static void assert_cache_initialized(bool initialized) {
  cr_assert(derived_key_cache_initialized == initialized);
  cr_assert(is_zero_key(&derived_key_cache.bip84_external_key) == !initialized);
  cr_assert(is_zero_key(&derived_key_cache.bip84_internal_key) == !initialized);
  cr_assert(is_zero_key(&derived_key_cache.w1_auth_key) == !initialized);
  cr_assert(fs_file_exists(DERIVED_KEY_CACHE_PATH) == initialized);
}

Test(derived_key_cache, populate, .init = setup, .fini = teardown) {
  extended_key_t zero_key = {0};
  extended_key_t key_priv = {0};
  derivation_path_t path = {0};

  // The cache won't be populated until we actually derive a path that is cached.
  path.indices = (uint32_t*)BIP84_MAINNET_INTERNAL_INDICES;
  path.num_indices = ARRAY_SIZE(BIP84_MAINNET_INTERNAL_INDICES) - 1;
  cr_assert(wallet_derive_key_priv_using_cache(&key_priv, path));
  cr_assert(memcmp(&key_priv, &zero_key, sizeof(extended_key_t)) != 0);
  assert_cache_initialized(false);

  memzero(&key_priv, sizeof(extended_key_t));

  path.indices = (uint32_t*)BIP84_MAINNET_INTERNAL_INDICES;
  path.num_indices = ARRAY_SIZE(BIP84_MAINNET_INTERNAL_INDICES);
  cr_assert(wallet_derive_key_priv_using_cache(&key_priv, path));
  cr_assert(memcmp(&key_priv, &zero_key, sizeof(extended_key_t)) != 0);
  assert_cache_initialized(true);

  extended_key_t key_priv_from_seed = {0};
  fingerprint_t master_fingerprint;
  fingerprint_t childs_parent_fingerprint;
  seed_res_t seed_res =
    seed_derive_bip32(path, &key_priv_from_seed, &master_fingerprint, &childs_parent_fingerprint);
  cr_assert(seed_res == SEED_RES_OK);
  cr_assert(memcmp(&key_priv_from_seed, &key_priv, sizeof(extended_key_t)) == 0);
}

Test(derived_key_cache, clear, .init = setup, .fini = teardown) {
  extended_key_t zero_key = {0};
  derivation_path_t path = {0};
  path.indices =
    (uint32_t[5]){84 | BIP32_HARDENED_BIT, 1 | BIP32_HARDENED_BIT, 0 | BIP32_HARDENED_BIT, 0, 1};
  path.num_indices = 5;

  extended_key_t key_priv1 = {0};
  cr_assert(wallet_derive_key_priv_using_cache(&key_priv1, path));
  cr_assert(memcmp(&key_priv1, &zero_key, sizeof(extended_key_t)) != 0);
  assert_cache_initialized(true);

  wallet_clear_derived_key_cache();

  cr_assert(derived_key_cache_initialized == false);
  cr_assert(memcmp(&derived_key_cache.bip84_external_key, &zero_key, sizeof(extended_key_t)) == 0);
  cr_assert(memcmp(&derived_key_cache.bip84_internal_key, &zero_key, sizeof(extended_key_t)) == 0);
  cr_assert(memcmp(&derived_key_cache.w1_auth_key, &zero_key, sizeof(extended_key_t)) == 0);
  // The file contains the keys encrypted, so it should still exist.
  cr_assert(fs_file_exists(DERIVED_KEY_CACHE_PATH));

  extended_key_t key_priv2 = {0};
  cr_assert(wallet_derive_key_priv_using_cache(&key_priv2, path));
  cr_assert(memcmp(&key_priv2, &key_priv1, sizeof(extended_key_t)) == 0);
  assert_cache_initialized(true);
}

Test(derived_key_cache, corrupt, .init = setup, .fini = teardown) {
  derivation_path_t path;
  path.indices = (uint32_t*)BIP84_MAINNET_INTERNAL_INDICES;
  path.num_indices = ARRAY_SIZE(BIP84_MAINNET_INTERNAL_INDICES);

  extended_key_t key_priv1 = {0};
  cr_assert(wallet_derive_key_priv_using_cache(&key_priv1, path));
  assert_cache_initialized(true);

  wallet_clear_derived_key_cache();

  // Corrupt the cache file by truncating the authentication tag from the ciphertext.
  fs_file_t cache_file = {0};
  fs_open(&cache_file, DERIVED_KEY_CACHE_PATH, FS_O_RDWR);
  fs_file_truncate(&cache_file, DERIVED_KEY_CACHE_CIPHERTEXT_SIZE - AES_GCM_OVERHEAD);
  fs_close(&cache_file);

  extended_key_t key_priv2 = {0};
  cr_assert(wallet_derive_key_priv_using_cache(&key_priv2, path));
  cr_assert(derived_key_cache_initialized == false);

  // We should still derive the key using the seed if the cache is corrupted.
  cr_assert(memcmp(&key_priv2, &key_priv1, sizeof(extended_key_t)) == 0);
}

Test(derived_key_cache, unknown_version, .init = setup, .fini = teardown) {
  derived_key_cache.version = 0xff;

  derivation_path_t path;
  path.indices = (uint32_t*)BIP84_MAINNET_INTERNAL_INDICES;
  path.num_indices = ARRAY_SIZE(BIP84_MAINNET_INTERNAL_INDICES);

  extended_key_t key_priv1 = {0};
  cr_assert(wallet_derive_key_priv_using_cache(&key_priv1, path));
  assert_cache_initialized(true);
  cr_assert(derived_key_cache.version == 0xff);

  wallet_clear_derived_key_cache();

  extended_key_t key_priv2 = {0};
  cr_assert(wallet_derive_key_priv_using_cache(&key_priv2, path));

  // The cache should be created using the latest known version.
  assert_cache_initialized(true);
  cr_assert(derived_key_cache.version == DERIVED_KEY_CACHE_VERSION);

  cr_assert(memcmp(&key_priv2, &key_priv1, sizeof(extended_key_t)) == 0);
}
