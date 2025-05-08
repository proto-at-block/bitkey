#include "FuzzedDataProvider.h"

extern "C" {
#include "aes.h"
#include "attributes.h"
#include "bd/lfs_emubd.h"
#include "bitlog.h"
#include "fff.h"
#include "filesystem.h"
#include "hex.h"
#include "key_management.h"
#include "lfs.h"
#include "secutils.h"
#include "wkek_impl.h"

#include <stdint.h>
#include <stdio.h>

FAKE_VALUE_FUNC(int, bd_erase_all);
FAKE_VALUE_FUNC(lfs_t*, bd_mount);
FAKE_VALUE_FUNC(bool, crypto_hkdf, key_handle_t*, hash_alg_t, uint8_t*, size_t, uint8_t*, size_t,
                key_handle_t*);
FAKE_VALUE_FUNC(bool, export_pubkey, key_handle_t*, key_handle_t*);

void refresh_auth(void) {}
bool bio_fingerprint_exists(void) {
  return true;
}
secure_bool_t is_authenticated(void) {
  return SECURE_TRUE;
}

bool bd_error_str(char* UNUSED(a), const size_t UNUSED(b), const int UNUSED(c)) {
  return false;
}
uint32_t rtos_event_group_set_bits(rtos_event_group_t* UNUSED(a), const uint32_t UNUSED(b)) {
  return 1;
}
uint32_t rtos_event_group_clear_bits(rtos_event_group_t* UNUSED(a), const uint32_t UNUSED(b)) {
  return 1;
}
uint32_t rtos_event_group_wait_bits(rtos_event_group_t* UNUSED(a), const uint32_t UNUSED(b),
                                    const bool UNUSED(c), const bool UNUSED(d),
                                    uint32_t UNUSED(e)) {
  return 1;
}
}

bool rtos_mutex_lock(rtos_mutex_t* UNUSED(t)) {
  return true;
}
bool rtos_mutex_take(rtos_mutex_t* UNUSED(a), uint32_t UNUSED(b)) {
  return true;
}
bool rtos_mutex_unlock(rtos_mutex_t* UNUSED(a)) {
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

DEFINE_FFF_GLOBALS;
FAKE_VOID_FUNC(rtos_mutex_create, rtos_mutex_t*);
FAKE_VOID_FUNC(rtos_semaphore_create, rtos_semaphore_t*);
bool rtos_semaphore_give(rtos_semaphore_t* UNUSED(s)) {
  return true;
}
bool rtos_semaphore_take(rtos_semaphore_t* UNUSED(s), uint32_t UNUSED(t)) {
  return true;
}
typedef void (*f_cb)(void*);
FAKE_VOID_FUNC(rtos_thread_create_static, rtos_thread_t*, f_cb, const char*, void*,
               rtos_thread_priority_t, uint32_t*, uint32_t, StaticTask_t*, rtos_thread_mpu_t);
FAKE_VOID_FUNC(rtos_thread_delete, rtos_thread_t*);
FAKE_VALUE_FUNC(uint64_t, rtos_thread_micros);
FAKE_VOID_FUNC(rtos_event_group_create, rtos_event_group_t*);
FAKE_VOID_FUNC(_putchar, char);

static lfs_t lfs;
static mempool_t* mempool;

#define FS_BLOCK_CYCLES   (500)          /* Wear leveling cycles */
#define FS_LOOKAHEAD_SIZE (128)          /* Must be a multiple of 8 */
#define FLASH_PAGE_SIZE   (0x00002000UL) /* Flash Memory page size */
#define FS_BLOCK_COUNT    16u            /* Number of flash blocks */

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

static key_descriptor_t descriptor;
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

static uint32_t timestamp(void) {
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

  assert(lfs_emubd_create(&cfg, &emubd_cfg) == 0);
  assert(lfs_format(&lfs, &cfg) == 0);
  assert(lfs_mount(&lfs, &cfg) == 0);

  descriptor.origin_fingerprint = (uint8_t*)malloc(BIP32_KEY_FINGERPRINT_SIZE);
  descriptor.serialized_bip32_key = (uint8_t*)malloc(BIP32_SERIALIZED_EXT_KEY_SIZE);
  descriptor.xpub_path = (uint32_t*)malloc(128);
  descriptor.origin_path = (uint32_t*)malloc(128);

  set_lfs(&lfs);
}

/* The following stages create a portion of the wallet */

/* Do not create wallet */
static void stage_0(void) {
  assert(wallet_created() == false);
  assert(wallet_set_network_type(BITCOIN));
}

/* Create partial wallet */
static void stage_1(void) {
  stage_0();

  assert(wkek_lazy_init() == true);
  assert(wkek_exists() == true);
  assert(wallet_created() == false);
}

/* Create and load full wallet */
static void stage_2(void) {
  stage_1();

  assert(wallet_create_keybundle(WALLET_KEY_BUNDLE_ACTIVE) == WALLET_RES_OK);
  assert(wallet_created() == true);
  fwpb_btc_network network = (fwpb_btc_network)99;  // Set to invalid type
  assert(wallet_get_network_type(&network));
  assert(network == BITCOIN);

  assert(wkek.key.size == sizeof(encrypted_wkek_buffer));
  assert(memcmp(encrypted_wkek_buffer, wkek.key.bytes, sizeof(encrypted_wkek_buffer)) == 0);
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  FuzzedDataProvider fuzzed_data(data, size);
  setup();

  // keep fuzzing harness enum types local
  enum class Wallet_Fuzzer_Setup { STAGE0 = 0, STAGE1, STAGE2, kMaxValue = STAGE2 };
  enum class Wallet_Fuzzer_Ops { SIGN_TXN = 0, ENCRYPT, DECRYPT, kMaxValue = DECRYPT };

  Wallet_Fuzzer_Setup setup_choice;
  Wallet_Fuzzer_Ops ops_choice;
  wallet_res_t expected[static_cast<int>(Wallet_Fuzzer_Ops::kMaxValue) +
                        1];  // map out expected responses to setup choice

  if (fuzzed_data.remaining_bytes() > 0) {
    setup_choice = fuzzed_data.ConsumeEnum<Wallet_Fuzzer_Setup>();

    // set up wallet to some degree (not at all, partially, fully)
    switch (setup_choice) {
      case Wallet_Fuzzer_Setup::STAGE0:
        stage_0();  // not at all
        expected[static_cast<int>(Wallet_Fuzzer_Ops::SIGN_TXN)] = WALLET_RES_NOT_CREATED;
        expected[static_cast<int>(Wallet_Fuzzer_Ops::ENCRYPT)] = WALLET_RES_OK;
        expected[static_cast<int>(Wallet_Fuzzer_Ops::DECRYPT)] = WALLET_RES_UNSEALING_ERR;
        break;
      case Wallet_Fuzzer_Setup::STAGE1:
        stage_1();  // partially
        expected[static_cast<int>(Wallet_Fuzzer_Ops::SIGN_TXN)] = WALLET_RES_NOT_CREATED;
        expected[static_cast<int>(Wallet_Fuzzer_Ops::ENCRYPT)] = WALLET_RES_OK;
        expected[static_cast<int>(Wallet_Fuzzer_Ops::DECRYPT)] = WALLET_RES_UNSEALING_ERR;
        break;
      case Wallet_Fuzzer_Setup::STAGE2:
      default:
        stage_2();  // fully
        expected[static_cast<int>(Wallet_Fuzzer_Ops::SIGN_TXN)] = WALLET_RES_OK;
        expected[static_cast<int>(Wallet_Fuzzer_Ops::ENCRYPT)] = WALLET_RES_OK;
        expected[static_cast<int>(Wallet_Fuzzer_Ops::DECRYPT)] = WALLET_RES_UNSEALING_ERR;
        break;
    }

    ops_choice = (Wallet_Fuzzer_Ops)fuzzed_data.ConsumeEnum<Wallet_Fuzzer_Ops>();
    // verify that wallet functions behave as expected with potentially incomplete setup
    switch (ops_choice) {
      case Wallet_Fuzzer_Ops::SIGN_TXN: {
        break;
      }
      case Wallet_Fuzzer_Ops::ENCRYPT: {
        uint8_t csek[CSEK_LENGTH] = {0};
        uint8_t encrypted_csek[CSEK_LENGTH] = {0};
        uint8_t iv[AES_GCM_IV_LENGTH] = {0};
        uint8_t tag[AES_GCM_TAG_LENGTH] = {0};
        assert(wallet_csek_encrypt(csek, encrypted_csek, sizeof(csek), iv, tag) ==
               expected[static_cast<int>(Wallet_Fuzzer_Ops::ENCRYPT)]);
        break;
      }
      case Wallet_Fuzzer_Ops::DECRYPT: {
        uint8_t encrypted_csek[CSEK_LENGTH] = {0};
        uint8_t csek_out[CSEK_LENGTH] = {0};
        uint8_t iv[AES_GCM_IV_LENGTH] = {0};
        uint8_t tag[AES_GCM_TAG_LENGTH] = {0};

        assert(wallet_csek_decrypt(encrypted_csek, csek_out, sizeof(csek_out), iv, tag) ==
               expected[static_cast<int>(Wallet_Fuzzer_Ops::DECRYPT)]);
        break;
      }
      default:
        break;
    }
  }

  free(descriptor.origin_fingerprint);
  free(descriptor.serialized_bip32_key);
  free(descriptor.xpub_path);
  free(descriptor.origin_path);
  lfs_emubd_destroy(&cfg);

  return 0;
}
