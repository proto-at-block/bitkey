#include "aes.h"
#include "attributes.h"
#include "bd/lfs_emubd.h"
#include "bitlog.h"
#include "criterion_test_utils.h"
#include "fff.h"
#include "filesystem.h"
#include "key_management.h"
#include "lfs.h"
#include "wallet.pb.h"
#include "wallet_impl.h"
#include "wkek_impl.h"

#include <criterion/criterion.h>
#include <criterion/new/assert.h>

#include <stdio.h>

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

FAKE_VALUE_FUNC(bool, crypto_hkdf, key_handle_t*, hash_alg_t, uint8_t*, size_t, uint8_t*, size_t,
                key_handle_t*);
FAKE_VALUE_FUNC(bool, export_pubkey, key_handle_t*, key_handle_t*);

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

extern uint8_t encrypted_wkek_buffer[AES_256_LENGTH_BYTES + SE_WRAPPED_KEY_OVERHEAD];
extern key_handle_t wkek;
static key_descriptor_t descriptor;

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
#define REGIONS(X)                                                       \
  X(wallet_pool, extended_keys, WALLET_POOL_R0_SIZE, WALLET_POOL_R0_NUM) \
  X(wallet_pool, r1, WALLET_POOL_R1_SIZE, WALLET_POOL_R1_NUM)
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

  descriptor.origin_fingerprint = malloc(BIP32_KEY_FINGERPRINT_SIZE);
  descriptor.serialized_bip32_key = malloc(BIP32_SERIALIZED_EXT_KEY_SIZE);
  descriptor.xpub_path = malloc(128);
  descriptor.origin_path = malloc(128);

  set_lfs(&lfs);
}

static void create_and_load(void) {
  cr_assert(wkek_exists() == false);
  cr_assert(wkek_lazy_init() == true);
  cr_assert(wkek_exists() == true);
  cr_assert(wallet_created() == false);
  cr_assert(wallet_create_keybundle(WALLET_KEY_BUNDLE_ACTIVE) == WALLET_RES_OK);
  cr_assert(wallet_created() == true);
  cr_assert(wallet_set_network_type(BITCOIN));
  fwpb_btc_network network = 99;  // Set to invalid type
  cr_assert(wallet_get_network_type(&network));
  cr_assert(network == BITCOIN);

  cr_assert(wkek.key.size == sizeof(encrypted_wkek_buffer));
  cr_util_cmp_buffers(encrypted_wkek_buffer, wkek.key.bytes, sizeof(encrypted_wkek_buffer));
}

void teardown(void) {
  lfs_emubd_destroy(&cfg);
}

Test(wallet, create_load_sign, .init = setup, .fini = teardown) {
  create_and_load();
  uint8_t sighash[32] = {0};
  uint8_t sig[64] = {0};
  cr_assert(wallet_sign_txn(WALLET_KEY_DOMAIN_SPEND, sighash, sig, 0, 0, &descriptor) ==
            WALLET_RES_OK);
}

Test(wallet, create_multiple_attempts, .init = setup, .fini = teardown) {
  // Multiple calls to create WKEK should work fine. This allows resuming
  // wallet creation.
  cr_assert(wkek_lazy_init() == true);
  cr_assert(wkek_lazy_init() == true);

  // Same for MK creation.
  cr_assert(wallet_create_keybundle(WALLET_KEY_BUNDLE_ACTIVE) == WALLET_RES_OK);
  cr_assert(wallet_create_keybundle(WALLET_KEY_BUNDLE_ACTIVE) == WALLET_RES_ALREADY_CREATED);
  cr_assert(wallet_create_keybundle(WALLET_KEY_BUNDLE_ACTIVE) == WALLET_RES_ALREADY_CREATED);
}

Test(wallet, lost_wkek, .init = setup, .fini = teardown) {
  create_and_load();
  memset(encrypted_wkek_buffer, 0, sizeof(encrypted_wkek_buffer));

  // Corrupt the WKEK file.
  uint8_t corrupted[32] = {0};
  memset(corrupted, 0xdf, sizeof(corrupted));
  cr_assert(fs_util_write_global(WKEK_PATH, corrupted, 32));

  // Using the WKEK to load the MK, and then signing should fail.
  uint8_t sighash[32] = {0};
  uint8_t sig[64] = {0};
  cr_assert(wallet_sign_txn(WALLET_KEY_DOMAIN_SPEND, sighash, sig, 0, 0, &descriptor) ==
            WALLET_RES_STORAGE_ERR);
}

Test(wallet, sign_stress, .init = setup, .fini = teardown) {
  create_and_load();

  // Signing many times shouldn't crash / cause memory leaks / etc.
  for (int i = 0; i < 1000; i++) {
    uint8_t sighash[32] = {0};
    uint8_t sig[64] = {0};
    cr_assert(wallet_sign_txn(WALLET_KEY_DOMAIN_SPEND, sighash, sig, 0, 0, &descriptor) ==
              WALLET_RES_OK);
  }
}

Test(wallet, load, .init = setup, .fini = teardown) {
  uint8_t sighash[32] = {0};
  uint8_t sig[64] = {0};

  create_and_load();

  // Generation puts WKEK in memory
  cr_assert(wallet_sign_txn(WALLET_KEY_DOMAIN_SPEND, sighash, sig, 0, 0, &descriptor) ==
            WALLET_RES_OK);

  // Clear to simulate RAM reset
  memset(encrypted_wkek_buffer, 0, sizeof(encrypted_wkek_buffer));

  // Shouldn't be able to load MK to sign
  cr_assert(wallet_sign_txn(WALLET_KEY_DOMAIN_SPEND, sighash, sig, 0, 0, &descriptor) ==
            WALLET_RES_STORAGE_ERR);
}

Test(wallet, seal_unseal_csek, .init = setup, .fini = teardown) {
  create_and_load();

  uint8_t csek[32] = {0};
  uint8_t encrypted_csek[32] = {0};
  uint8_t csek_out[32] = {0};
  uint8_t iv[12] = {0};
  uint8_t tag[16] = {0};

  cr_assert(wallet_csek_encrypt(csek, encrypted_csek, sizeof(csek), iv, tag) == WALLET_RES_OK);
  cr_assert(wallet_csek_decrypt(encrypted_csek, csek_out, sizeof(csek), iv, tag) == WALLET_RES_OK);

  cr_util_cmp_buffers(csek, csek_out, sizeof(csek));
}

Test(wallet, get_pubkey, .init = setup, .fini = teardown) {
  create_and_load();

  extended_key_t pub_key = {0};
  wallet_res_t result =
    wallet_get_pubkey(WALLET_KEY_BUNDLE_ACTIVE, WALLET_KEY_DOMAIN_AUTH, &pub_key);
  cr_assert(result == WALLET_RES_OK);

  result = wallet_get_pubkey(WALLET_KEY_BUNDLE_ACTIVE, WALLET_KEY_DOMAIN_CONFIG, &pub_key);
  cr_assert(result == WALLET_RES_OK);

  result = wallet_get_pubkey(WALLET_KEY_BUNDLE_ACTIVE, WALLET_KEY_DOMAIN_SPEND, &pub_key);
  cr_assert(result == WALLET_RES_OK);
}

Test(wallet, keybundle_id, .init = setup, .fini = teardown) {
  // Calculates a key bundle ID without loading a key, so all pub keys will be 0x00
  // This allows for a deterministic output to test against

  uint8_t id_digest[SHA256_DIGEST_SIZE] = {0};
  wallet_res_t result = wallet_keybundle_id(WALLET_KEY_BUNDLE_ACTIVE, id_digest);
  cr_assert(result == WALLET_RES_OK);

  uint8_t expected_id[] = {
    0x4b, 0x29, 0x80, 0x58, 0xe1, 0xd5, 0xfd, 0x3f, 0x2f, 0xa2, 0x0e, 0xad, 0x21, 0x77, 0x39, 0x12,
    0xa5, 0xdc, 0x38, 0xda, 0x3c, 0x0d, 0xa0, 0xbb, 0xc7, 0xde, 0x1a, 0xdf, 0xb6, 0x01, 0x1f, 0x1c,
  };
  cr_assert(memcmp(id_digest, expected_id, SHA256_DIGEST_SIZE) == 0);
}
