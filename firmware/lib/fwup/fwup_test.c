#include "application_properties.h"
#include "arithmetic.h"
#include "attributes.h"
#include "bd/lfs_emubd.h"
#include "bitlog.h"
#include "criterion_test_utils.h"
#include "fff.h"
#include "filesystem.h"
#include "fwup.h"
#include "fwup_delta_impl.h"
#include "fwup_flash_impl.h"
#include "fwup_impl.h"
#include "fwup_staged_sig.h"
#include "hex.h"
#include "rtos.h"
#include "security_config.h"
#include "secutils.h"

#include <criterion/criterion.h>
#include <criterion/new/assert.h>

#include <stdint.h>
#include <string.h>

DEFINE_FFF_GLOBALS;
FAKE_VOID_FUNC(_putchar, char);
FAKE_VOID_FUNC(secure_glitch_random_delay);
FAKE_VALUE_FUNC(bool, bd_error_str, char*, const size_t, const int);
FAKE_VALUE_FUNC(int, bd_erase_all);
FAKE_VALUE_FUNC(lfs_t*, bd_mount);
FAKE_VOID_FUNC(rtos_event_group_create, rtos_event_group_t*);
FAKE_VALUE_FUNC(uint32_t, rtos_event_group_set_bits, rtos_event_group_t*, const uint32_t);
FAKE_VALUE_FUNC(bool, rtos_event_group_set_bits_from_isr, rtos_event_group_t*, const uint32_t,
                bool*);
FAKE_VALUE_FUNC(uint32_t, rtos_event_group_get_bits, rtos_event_group_t*);
FAKE_VALUE_FUNC(uint32_t, rtos_event_group_wait_bits, rtos_event_group_t*, const uint32_t,
                const bool, const bool, uint32_t);
FAKE_VALUE_FUNC(uint32_t, rtos_event_group_clear_bits, rtos_event_group_t*, const uint32_t);
FAKE_VOID_FUNC(rtos_semaphore_create, rtos_semaphore_t*);

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
void rtos_mutex_create(rtos_mutex_t* UNUSED(mutex)) {}
typedef void (*f_cb)(void*);
FAKE_VOID_FUNC(rtos_thread_create_static, rtos_thread_t*, f_cb, const char*, void*,
               rtos_thread_priority_t, uint32_t*, uint32_t, StaticTask_t*, rtos_thread_mpu_t);
FAKE_VOID_FUNC(rtos_thread_delete, rtos_thread_t*);

security_config_t security_config = {0};
rtos_thread_mpu_t _fs_mount_task_regions;

// Stub definitions for metadata.c (metadata_get_active_slot() will return METADATA_MISSING)
size_t active_slot = 0;
size_t bl_metadata_size = 0;
size_t bl_metadata_page = 0;
size_t app_a_metadata_size = 0;
size_t app_a_metadata_page = 0;
size_t app_b_metadata_size = 0;
size_t app_b_metadata_page = 0;

const ApplicationCertificate_t app_certificate = {
  .structVersion = APPLICATION_CERTIFICATE_VERSION,
  .flags = {0U},
  .key = {0U},
  .version = 0,
  .signature = {0U},
};

const uint32_t app_properties_version = 0;

#define APP_PROPERTIES_ID \
  { 0 }

USED ApplicationProperties_t sl_app_properties = {
  .magic = APPLICATION_PROPERTIES_MAGIC,
  .structVersion = APPLICATION_PROPERTIES_VERSION,
  .signatureType = APPLICATION_SIGNATURE_ECDSA_P256,
  .signatureLocation = 0,
  .app =
    {
      .type = APPLICATION_TYPE_MCU,
      .version = app_properties_version,
      .capabilities = 0,
      .productId = APP_PROPERTIES_ID,
    },
  .cert = (ApplicationCertificate_t*)&app_certificate,
};

USED uint8_t app_codesigning_signature[64] = {0};

#define FIRMWARE_SLOT_SIZE (632 * 1024)
uint8_t firmware_a_slot[FIRMWARE_SLOT_SIZE] = {0};
uint8_t firmware_b_slot[FIRMWARE_SLOT_SIZE] = {0};

static lfs_t lfs;
#define FS_BLOCK_CYCLES   (500)
#define FS_LOOKAHEAD_SIZE (128)
#define FLASH_PAGE_SIZE   (0x00002000UL)
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
const struct lfs_config cfg = {
  .read = lfs_emubd_read,
  .prog = lfs_emubd_prog,
  .erase = lfs_emubd_erase,
  .sync = lfs_emubd_sync,
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

static void init_lfs(void) {
  cr_assert(lfs_emubd_create(&cfg, &emubd_cfg) == 0);
  cr_assert(lfs_format(&lfs, &cfg) == 0);
  cr_assert(lfs_mount(&lfs, &cfg) == 0);
  set_lfs(&lfs);
}

static void cleanup_lfs(void) {
  set_lfs(NULL);
  lfs_emubd_destroy(&cfg);
}

static void start(fwpb_fwup_mode mode, uint32_t patch_size) {
  fwpb_fwup_start_cmd cmd = fwpb_fwup_start_cmd_init_default;
  cmd.mode = mode;
  cmd.patch_size = patch_size;
  fwpb_fwup_start_rsp rsp = fwpb_fwup_start_rsp_init_default;
  cr_assert(fwup_start(&cmd, &rsp));
  cr_assert(rsp.rsp_status == fwpb_fwup_start_rsp_fwup_start_rsp_status_SUCCESS);
}

static void start_expect_error(fwpb_fwup_mode mode, uint32_t patch_size) {
  fwpb_fwup_start_cmd cmd = fwpb_fwup_start_cmd_init_default;
  cmd.mode = mode;
  cmd.patch_size = patch_size;
  fwpb_fwup_start_rsp rsp = fwpb_fwup_start_rsp_init_default;
  cr_assert_not(fwup_start(&cmd, &rsp));
  cr_assert(rsp.rsp_status == fwpb_fwup_start_rsp_fwup_start_rsp_status_ERROR);
}

static void start_with_version(uint32_t major, uint32_t minor, uint32_t patch) {
  fwpb_fwup_start_cmd cmd = fwpb_fwup_start_cmd_init_default;
  cmd.mode = fwpb_fwup_mode_FWUP_MODE_NORMAL;
  cmd.has_version = true;
  cmd.version.major = major;
  cmd.version.minor = minor;
  cmd.version.patch = patch;
  fwpb_fwup_start_rsp rsp = fwpb_fwup_start_rsp_init_default;
  cr_assert(fwup_start(&cmd, &rsp));
  cr_assert(rsp.rsp_status == fwpb_fwup_start_rsp_fwup_start_rsp_status_SUCCESS);
}

// Transfer a dummy signature chunk to the signature region so that
// fwup_finish() finds has_pending_signature == true.
static void transfer_dummy_signature(void) {
  fwpb_fwup_transfer_cmd cmd = fwpb_fwup_transfer_cmd_init_default;
  fwpb_fwup_transfer_rsp rsp = fwpb_fwup_transfer_rsp_init_default;

  const uint32_t sig_offset = FIRMWARE_SLOT_SIZE - FWUP_SIGNATURE_SIZE;
  const uint32_t max_chunk_size = (uint32_t)fwup_flash_get_max_chunk_size();

  cmd.sequence_id = sig_offset / max_chunk_size;
  cmd.offset = sig_offset % max_chunk_size;
  memset(cmd.fwup_data.bytes, 0xDD, FWUP_SIGNATURE_SIZE);
  cmd.fwup_data.size = FWUP_SIGNATURE_SIZE;

  cr_assert(fwup_transfer(&cmd, &rsp));
  cr_assert(rsp.rsp_status == fwpb_fwup_transfer_rsp_fwup_transfer_rsp_status_SUCCESS);
}

static void finish(void) {
  fwpb_fwup_finish_cmd cmd = fwpb_fwup_finish_cmd_init_default;
  fwpb_fwup_finish_rsp rsp = fwpb_fwup_finish_rsp_init_default;

  cmd.app_properties_offset = 1024;
  cmd.signature_offset = FIRMWARE_SLOT_SIZE - 64;

  fwup_finish(&cmd, &rsp);
  cr_assert(rsp.rsp_status == fwpb_fwup_finish_rsp_fwup_finish_rsp_status_SUCCESS);
}

static uint32_t timestamp(void) {
  return 0;
}

static void init_test_logging(void) {
  bitlog_init((bitlog_api_t){
    .timestamp_cb = timestamp,
  });
}

extern fwup_priv_t fwup_priv;

enum {
  DELTA_START_TEST_SLOT_SIZE = 128,  // Must be > FWUP_SIGNATURE_SIZE (64)
};

static uintptr_t overflowing_aligned_slot_base(void) {
  return UINTPTR_MAX - (uintptr_t)(DELTA_START_TEST_SLOT_SIZE - 1u);
}

void setup(void) {
  init_test_logging();
  fwup_init(firmware_b_slot, firmware_a_slot, &firmware_b_slot[FIRMWARE_SLOT_SIZE - 64],
            FIRMWARE_SLOT_SIZE, true, SECURE_FALSE);
  start(fwpb_fwup_mode_FWUP_MODE_NORMAL, 0);
}

void setup_delta_oneshot(void) {
  init_test_logging();
  init_lfs();
  fwup_init(firmware_b_slot, firmware_a_slot,
            &firmware_b_slot[FIRMWARE_SLOT_SIZE - FLASH_PAGE_SIZE], FIRMWARE_SLOT_SIZE, true,
            SECURE_FALSE);
  start(fwpb_fwup_mode_FWUP_MODE_DELTA_ONESHOT, 1024);
}

void teardown_delta_oneshot(void) {
  cleanup_lfs();
}

void setup_require_confirmation(void) {
  init_test_logging();
  fwup_init(firmware_b_slot, firmware_a_slot, &firmware_b_slot[FIRMWARE_SLOT_SIZE - 64],
            FIRMWARE_SLOT_SIZE, true, SECURE_TRUE);
  start(fwpb_fwup_mode_FWUP_MODE_NORMAL, 0);
}

void setup_with_version(void) {
  init_test_logging();
  fwup_init(firmware_b_slot, firmware_a_slot, &firmware_b_slot[FIRMWARE_SLOT_SIZE - 64],
            FIRMWARE_SLOT_SIZE, true, SECURE_TRUE);
  start_with_version(1, 2, 3);
}

void setup_init_only(void) {
  bitlog_init((bitlog_api_t){
    .timestamp_cb = timestamp,
  });
  fwup_init(firmware_b_slot, firmware_a_slot, &firmware_b_slot[FIRMWARE_SLOT_SIZE - 64],
            FIRMWARE_SLOT_SIZE, true, false);
}

Test(fwup, single_chunk, .init = setup) {
  fwpb_fwup_transfer_cmd cmd = fwpb_fwup_transfer_cmd_init_default;
  fwpb_fwup_transfer_rsp rsp = fwpb_fwup_transfer_rsp_init_default;

  cmd.sequence_id = 0;
  memset(cmd.fwup_data.bytes, 0xab, sizeof(cmd.fwup_data.bytes));
  cmd.fwup_data.size = sizeof(cmd.fwup_data.bytes);

  cr_assert(fwup_transfer(&cmd, &rsp));
  cr_assert(rsp.rsp_status == fwpb_fwup_transfer_rsp_fwup_transfer_rsp_status_SUCCESS);

  cr_util_cmp_buffers(firmware_b_slot, cmd.fwup_data.bytes, cmd.fwup_data.size);

  transfer_dummy_signature();
  finish();
}

Test(fwup, transfer_regular_sized_image, .init = setup) {
  uint32_t id = 0;
  uint32_t len = 0;

  while (len < (450 * 1024)) {
    fwpb_fwup_transfer_cmd cmd = fwpb_fwup_transfer_cmd_init_default;
    fwpb_fwup_transfer_rsp rsp = fwpb_fwup_transfer_rsp_init_default;

    cmd.sequence_id = id;
    memset(cmd.fwup_data.bytes, 0xab, sizeof(cmd.fwup_data.bytes));
    cmd.fwup_data.size = sizeof(cmd.fwup_data.bytes);

    cr_assert(fwup_transfer(&cmd, &rsp));
    cr_assert(rsp.rsp_status == fwpb_fwup_transfer_rsp_fwup_transfer_rsp_status_SUCCESS);

    cr_util_cmp_buffers(&firmware_b_slot[len], cmd.fwup_data.bytes, cmd.fwup_data.size);

    len += cmd.fwup_data.size;
    id++;
  }

  transfer_dummy_signature();
  finish();
}

Test(fwup, out_of_order_chunks, .init = setup) {
  uint8_t a[FIELD_SIZEOF(fwpb_fwup_transfer_cmd_fwup_data_t, bytes)];
  uint8_t b[FIELD_SIZEOF(fwpb_fwup_transfer_cmd_fwup_data_t, bytes)];
  uint8_t c[FIELD_SIZEOF(fwpb_fwup_transfer_cmd_fwup_data_t, bytes)];

  memset(a, 0xaa, sizeof(a));
  memset(b, 0xbb, sizeof(b));
  memset(c, 0xcc, sizeof(c));

  uint32_t off = 0;

  // Send the pattern aaaa...bbbb...cccc... out of order, and ensure
  // the final firmware slot is the correct pattern.

  {
    fwpb_fwup_transfer_cmd cmd = fwpb_fwup_transfer_cmd_init_default;
    fwpb_fwup_transfer_rsp rsp = fwpb_fwup_transfer_rsp_init_default;

    cmd.sequence_id = 0;
    memcpy(cmd.fwup_data.bytes, a, sizeof(a));
    cmd.fwup_data.size = sizeof(a);

    cr_assert(fwup_transfer(&cmd, &rsp));
    cr_assert(rsp.rsp_status == fwpb_fwup_transfer_rsp_fwup_transfer_rsp_status_SUCCESS);

    cr_util_cmp_buffers(&firmware_b_slot[off], a, sizeof(a));
  }

  {
    fwpb_fwup_transfer_cmd cmd = fwpb_fwup_transfer_cmd_init_default;
    fwpb_fwup_transfer_rsp rsp = fwpb_fwup_transfer_rsp_init_default;

    cmd.sequence_id = 2;
    memcpy(cmd.fwup_data.bytes, c, sizeof(c));
    cmd.fwup_data.size = sizeof(c);

    cr_assert(fwup_transfer(&cmd, &rsp));
    cr_assert(rsp.rsp_status == fwpb_fwup_transfer_rsp_fwup_transfer_rsp_status_SUCCESS);
  }

  {
    fwpb_fwup_transfer_cmd cmd = fwpb_fwup_transfer_cmd_init_default;
    fwpb_fwup_transfer_rsp rsp = fwpb_fwup_transfer_rsp_init_default;

    cmd.sequence_id = 1;
    memcpy(cmd.fwup_data.bytes, b, sizeof(b));
    cmd.fwup_data.size = sizeof(b);

    cr_assert(fwup_transfer(&cmd, &rsp));
    cr_assert(rsp.rsp_status == fwpb_fwup_transfer_rsp_fwup_transfer_rsp_status_SUCCESS);
  }

  uint8_t expected[sizeof(a) + sizeof(b) + sizeof(c)];
  memcpy(expected, a, sizeof(a));
  memcpy(expected + sizeof(a), b, sizeof(b));
  memcpy(expected + sizeof(a) + sizeof(b), c, sizeof(c));

  cr_util_cmp_buffers(firmware_b_slot, expected, sizeof(expected));

  transfer_dummy_signature();
  finish();
}

Test(fwup, transfer_bad_write_address, .init = setup) {
  fwpb_fwup_transfer_cmd cmd = fwpb_fwup_transfer_cmd_init_default;
  fwpb_fwup_transfer_rsp rsp = fwpb_fwup_transfer_rsp_init_default;

  cmd.sequence_id = 100000;  // Arbitrarily too-large number
  cmd.offset = 0;
  cmd.fwup_data.size = FIELD_SIZEOF(fwpb_fwup_transfer_cmd_fwup_data_t, bytes);

  cr_assert(fwup_transfer(&cmd, &rsp) == false);
  cr_assert(rsp.rsp_status == fwpb_fwup_transfer_rsp_fwup_transfer_rsp_status_ERROR);
}

// delta_start_rejects_zero_slot_size removed: fwup_init() now asserts
// slot_size > FWUP_SIGNATURE_SIZE, so zero can never reach fwup_delta_init().

Test(fwup, delta_start_rejects_active_slot_address_overflow) {
  init_test_logging();
  fwup_init(firmware_b_slot, (void*)overflowing_aligned_slot_base(),
            &firmware_b_slot[FIRMWARE_SLOT_SIZE - 64], DELTA_START_TEST_SLOT_SIZE, true,
            SECURE_FALSE);

  start_expect_error(fwpb_fwup_mode_FWUP_MODE_DELTA_ONESHOT, 1024);
}

Test(fwup, delta_start_rejects_target_slot_address_overflow) {
  init_test_logging();
  fwup_init((void*)overflowing_aligned_slot_base(), firmware_a_slot,
            &firmware_b_slot[FIRMWARE_SLOT_SIZE - 64], DELTA_START_TEST_SLOT_SIZE, true,
            SECURE_FALSE);

  start_expect_error(fwpb_fwup_mode_FWUP_MODE_DELTA_ONESHOT, 1024);
}

Test(fwup, transfer_wrapped_write_address, .init = setup) {
  fwpb_fwup_transfer_cmd cmd = fwpb_fwup_transfer_cmd_init_default;
  fwpb_fwup_transfer_rsp rsp = fwpb_fwup_transfer_rsp_init_default;

  const uint32_t max_chunk_size = (uint32_t)fwup_flash_get_max_chunk_size();
  cr_assert(max_chunk_size > 0);

  cmd.sequence_id = (UINT32_MAX / max_chunk_size) + 1;
  cmd.offset = 0;
  memset(cmd.fwup_data.bytes, 0xab, sizeof(cmd.fwup_data.bytes));
  cmd.fwup_data.size = 1;

  cr_assert(fwup_transfer(&cmd, &rsp) == false);
  cr_assert(rsp.rsp_status == fwpb_fwup_transfer_rsp_fwup_transfer_rsp_status_ERROR);
  cr_assert_eq(firmware_b_slot[0], 0xff);
}

Test(fwup, transfer_wrapped_write_address_delta_oneshot, .init = setup_delta_oneshot,
     .fini = teardown_delta_oneshot) {
  fwpb_fwup_transfer_cmd cmd = fwpb_fwup_transfer_cmd_init_default;
  fwpb_fwup_transfer_rsp rsp = fwpb_fwup_transfer_rsp_init_default;

  const uint32_t max_chunk_size = (uint32_t)fwup_flash_get_max_chunk_size();
  cr_assert(max_chunk_size > 0);

  cmd.mode = fwpb_fwup_mode_FWUP_MODE_DELTA_ONESHOT;
  cmd.sequence_id = (UINT32_MAX / max_chunk_size) + 1;
  cmd.offset = 0;
  memset(cmd.fwup_data.bytes, 0xab, sizeof(cmd.fwup_data.bytes));
  cmd.fwup_data.size = 1;

  cr_assert(fwup_transfer(&cmd, &rsp) == false);
  cr_assert(rsp.rsp_status == fwpb_fwup_transfer_rsp_fwup_transfer_rsp_status_ERROR);
}

Test(fwup, transfer_bad_offset, .init = setup) {
  fwpb_fwup_transfer_cmd cmd = fwpb_fwup_transfer_cmd_init_default;
  fwpb_fwup_transfer_rsp rsp = fwpb_fwup_transfer_rsp_init_default;

  cmd.offset = 450 * 10000;  // Arbitrarily too-large number
  cmd.fwup_data.size = FIELD_SIZEOF(fwpb_fwup_transfer_cmd_fwup_data_t, bytes);

  cr_assert(fwup_transfer(&cmd, &rsp) == false);
  cr_assert(rsp.rsp_status == fwpb_fwup_transfer_rsp_fwup_transfer_rsp_status_ERROR);
}

Test(fwup, finish_bad_properties_offset, .init = setup) {
  transfer_dummy_signature();

  fwpb_fwup_finish_cmd cmd = fwpb_fwup_finish_cmd_init_default;
  fwpb_fwup_finish_rsp rsp = fwpb_fwup_finish_rsp_init_default;

  cmd.app_properties_offset = 1024 * 10000;  // Arbitrarily too-large number
  cmd.signature_offset = FIRMWARE_SLOT_SIZE - 64;

  fwup_finish(&cmd, &rsp);
  cr_assert(rsp.rsp_status == fwpb_fwup_finish_rsp_fwup_finish_rsp_status_ERROR);
}

Test(fwup, finish_bad_signature_offset, .init = setup) {
  transfer_dummy_signature();

  fwpb_fwup_finish_cmd cmd = fwpb_fwup_finish_cmd_init_default;
  fwpb_fwup_finish_rsp rsp = fwpb_fwup_finish_rsp_init_default;

  cmd.app_properties_offset = 1024;
  cmd.signature_offset = 800 * 10000;  // Mismatched offset — should be rejected

  fwup_finish(&cmd, &rsp);
  cr_assert(rsp.rsp_status == fwpb_fwup_finish_rsp_fwup_finish_rsp_status_ERROR);
}

Test(fwup, no_version_skips_mismatch_check, .init = setup) {
  transfer_dummy_signature();

  fwpb_fwup_finish_cmd cmd = fwpb_fwup_finish_cmd_init_default;
  fwpb_fwup_finish_rsp rsp = fwpb_fwup_finish_rsp_init_default;

  cmd.app_properties_offset = 1024;
  cmd.signature_offset = FIRMWARE_SLOT_SIZE - 64;

  fwup_finish(&cmd, &rsp);
  cr_assert(rsp.rsp_status == fwpb_fwup_finish_rsp_fwup_finish_rsp_status_SUCCESS);
}

Test(fwup, require_confirmation_missing_version_is_mismatch, .init = setup_require_confirmation) {
  transfer_dummy_signature();

  fwpb_fwup_finish_cmd cmd = fwpb_fwup_finish_cmd_init_default;
  fwpb_fwup_finish_rsp rsp = fwpb_fwup_finish_rsp_init_default;

  cmd.app_properties_offset = 1024;
  cmd.signature_offset = FIRMWARE_SLOT_SIZE - 64;

  fwup_finish(&cmd, &rsp);
  cr_assert(rsp.rsp_status == fwpb_fwup_finish_rsp_fwup_finish_rsp_status_CONFIRMATION_MISMATCH);
}

Test(fwup, mismatch_when_metadata_unavailable, .init = setup_with_version) {
  transfer_dummy_signature();

  fwpb_fwup_finish_cmd cmd = fwpb_fwup_finish_cmd_init_default;
  fwpb_fwup_finish_rsp rsp = fwpb_fwup_finish_rsp_init_default;

  cmd.app_properties_offset = 1024;
  cmd.signature_offset = FIRMWARE_SLOT_SIZE - 64;

  fwup_finish(&cmd, &rsp);
  cr_assert(rsp.rsp_status == fwpb_fwup_finish_rsp_fwup_finish_rsp_status_CONFIRMATION_MISMATCH);
}

Test(fwup, confirmation_mismatch_does_not_write_signature, .init = setup_with_version) {
  // Write identifiable data to the target slot.
  fwpb_fwup_transfer_cmd transfer_cmd = fwpb_fwup_transfer_cmd_init_default;
  fwpb_fwup_transfer_rsp transfer_rsp = fwpb_fwup_transfer_rsp_init_default;
  transfer_cmd.sequence_id = 0;
  memset(transfer_cmd.fwup_data.bytes, 0xAB, sizeof(transfer_cmd.fwup_data.bytes));
  transfer_cmd.fwup_data.size = sizeof(transfer_cmd.fwup_data.bytes);
  cr_assert(fwup_transfer(&transfer_cmd, &transfer_rsp));
  cr_assert(firmware_b_slot[0] == 0xAB);  // confirm data was written

  transfer_dummy_signature();

  fwpb_fwup_finish_cmd finish_cmd = fwpb_fwup_finish_cmd_init_default;
  fwpb_fwup_finish_rsp finish_rsp = fwpb_fwup_finish_rsp_init_default;
  finish_cmd.app_properties_offset = 1024;
  finish_cmd.signature_offset = FIRMWARE_SLOT_SIZE - FWUP_SIGNATURE_SIZE;

  // Version metadata is not in the slot, so the confirmation check will fail.
  cr_assert(fwup_finish(&finish_cmd, &finish_rsp) == false);
  cr_assert(finish_rsp.rsp_status ==
            fwpb_fwup_finish_rsp_fwup_finish_rsp_status_CONFIRMATION_MISMATCH);

  // Firmware content remains but the signature is not in flash, so the
  // bootloader will not consider this slot valid.
  cr_assert(firmware_b_slot[0] == 0xAB);  // firmware content still present
  const uint32_t sig_start = FIRMWARE_SLOT_SIZE - FWUP_SIGNATURE_SIZE;
  for (uint32_t i = 0; i < FWUP_SIGNATURE_SIZE; i++) {
    cr_assert_eq(firmware_b_slot[sig_start + i], 0xFF);
  }
}

// -- fwup_pre_apply_check tests --

// For non-delta modes fwup_pre_apply_check is a no-op that always returns true,
// regardless of require_confirmation or has_confirmation_version.
Test(fwup, pre_apply_check_noop_for_normal_mode, .init = setup_require_confirmation) {
  fwpb_fwup_finish_cmd cmd = fwpb_fwup_finish_cmd_init_default;
  fwpb_fwup_finish_rsp rsp = fwpb_fwup_finish_rsp_init_default;
  cmd.mode = fwpb_fwup_mode_FWUP_MODE_NORMAL;
  cr_assert(fwup_pre_apply_check(&cmd, &rsp));
}

// -- fwup_delta bounds helper tests --

Test(fwup_delta_bounds, slot_range_valid_rejects_overrun) {
  cr_assert(fwup_delta_slot_range_valid(32, 0, 32));
  cr_assert(fwup_delta_slot_range_valid(32, 32, 0));
  cr_assert_not(fwup_delta_slot_range_valid(32, 32, 1));
  cr_assert_not(fwup_delta_slot_range_valid(32, 31, 2));
}

Test(fwup_delta_bounds, slot_seek_rejects_underflow_and_overflow) {
  size_t next_offset = 0;

  cr_assert(fwup_delta_slot_seek(32, 12, -12, &next_offset));
  cr_assert_eq(next_offset, 0);

  cr_assert(fwup_delta_slot_seek(32, 12, 20, &next_offset));
  cr_assert_eq(next_offset, 32);

  cr_assert_not(fwup_delta_slot_seek(32, 12, -13, &next_offset));
  cr_assert_not(fwup_delta_slot_seek(32, 12, 21, &next_offset));
  cr_assert_not(fwup_delta_slot_seek(32, 33, 0, &next_offset));
}

// -- fwup_delta_check_header unit tests --

static void build_v1_header(uint8_t* buf, uint8_t major, uint8_t minor, uint8_t patch) {
  const uint8_t magic[] = FWUP_DELTA_HEADER_MAGIC_BYTES;
  memcpy(buf, magic, FWUP_DELTA_HEADER_MAGIC_SIZE);
  buf[4] = FWUP_DELTA_HEADER_VERSION_1;
  buf[5] = FWUP_DELTA_HEADER_V1_SIZE;
  buf[6] = major;
  buf[7] = minor;
  buf[8] = patch;
}

Test(fwup_delta_header, matching_version_passes, .init = setup_with_version) {
  uint8_t header[FWUP_DELTA_HEADER_V1_SIZE];
  build_v1_header(header, 1, 2, 3);

  fwpb_semver expected = {.major = 1, .minor = 2, .patch = 3};
  cr_assert(fwup_delta_check_header_from_buf(header, sizeof(header), &expected, true) ==
            FWUP_VERIFY_SUCCESS);
}

Test(fwup_delta_header, mismatched_version_fails, .init = setup_with_version) {
  uint8_t header[FWUP_DELTA_HEADER_V1_SIZE];
  build_v1_header(header, 9, 9, 9);

  fwpb_semver expected = {.major = 1, .minor = 2, .patch = 3};
  cr_assert(fwup_delta_check_header_from_buf(header, sizeof(header), &expected, true) ==
            FWUP_VERIFY_CONFIRMATION_MISMATCH);
}

Test(fwup_delta_header, no_magic_with_require_header_fails, .init = setup_require_confirmation) {
  uint8_t header[FWUP_DELTA_HEADER_V1_SIZE];
  memset(header, 0xAB, sizeof(header));  // no valid magic

  cr_assert(fwup_delta_check_header_from_buf(header, sizeof(header), NULL, true) ==
            FWUP_VERIFY_MISSING_HEADER);
}

Test(fwup_delta_header, no_magic_without_require_header_passes, .init = setup) {
  uint8_t header[FWUP_DELTA_HEADER_V1_SIZE];
  memset(header, 0xAB, sizeof(header));  // no valid magic

  cr_assert(fwup_delta_check_header_from_buf(header, sizeof(header), NULL, false) ==
            FWUP_VERIFY_SUCCESS);
}

// -- verify-before-signature tests --

// Signature bytes sent via fwup_transfer() should be intercepted into the RAM
// buffer and NOT written to the flash signature region.
Test(fwup, transfer_signature_intercepted_to_ram, .init = setup) {
  transfer_dummy_signature();

  // The signature region in flash must still be erased (0xFF).
  const uint32_t sig_start = FIRMWARE_SLOT_SIZE - FWUP_SIGNATURE_SIZE;
  for (uint32_t i = 0; i < FWUP_SIGNATURE_SIZE; i++) {
    cr_assert_eq(firmware_b_slot[sig_start + i], 0xFF,
                 "flash sig byte %u should be 0xFF, got 0x%02X", i, firmware_b_slot[sig_start + i]);
  }

  // The RAM buffer must contain the signature data.
  cr_assert(fwup_priv.has_pending_signature);
  for (uint32_t i = 0; i < FWUP_SIGNATURE_SIZE; i++) {
    cr_assert_eq(fwup_priv.pending_signature[i], 0xDD,
                 "pending_signature[%u] should be 0xDD, got 0x%02X", i,
                 fwup_priv.pending_signature[i]);
  }
}

// A chunk that straddles the firmware/signature boundary should split: the
// firmware portion goes to flash, the signature portion goes to RAM.
Test(fwup, transfer_split_chunk_at_signature_boundary, .init = setup) {
  const uint32_t sig_boundary = FIRMWARE_SLOT_SIZE - FWUP_SIGNATURE_SIZE;
  // Write a chunk that starts 16 bytes before the boundary and extends 16 bytes into it.
  const uint32_t chunk_start = sig_boundary - 16;
  const uint32_t chunk_size = 32;

  fwpb_fwup_transfer_cmd cmd = fwpb_fwup_transfer_cmd_init_default;
  fwpb_fwup_transfer_rsp rsp = fwpb_fwup_transfer_rsp_init_default;

  const uint32_t max_chunk_size = (uint32_t)fwup_flash_get_max_chunk_size();
  cmd.sequence_id = chunk_start / max_chunk_size;
  cmd.offset = chunk_start % max_chunk_size;

  // Fill first 16 bytes with 0xAA (flash), next 16 with 0xBB (signature).
  memset(cmd.fwup_data.bytes, 0xAA, 16);
  memset(cmd.fwup_data.bytes + 16, 0xBB, 16);
  cmd.fwup_data.size = chunk_size;

  cr_assert(fwup_transfer(&cmd, &rsp));

  // Flash portion should be written.
  for (uint32_t i = 0; i < 16; i++) {
    cr_assert_eq(firmware_b_slot[chunk_start + i], 0xAA);
  }

  // Signature region in flash should still be erased.
  for (uint32_t i = 0; i < 16; i++) {
    cr_assert_eq(firmware_b_slot[sig_boundary + i], 0xFF);
  }

  // RAM buffer should have the signature bytes.
  cr_assert(fwup_priv.has_pending_signature);
  for (uint32_t i = 0; i < 16; i++) {
    cr_assert_eq(fwup_priv.pending_signature[i], 0xBB);
  }
}

// After a successful fwup_finish() + fwup_commit_signature(), the signature
// must be written to flash.
Test(fwup, finish_writes_signature_to_flash, .init = setup) {
  transfer_dummy_signature();
  finish();

  // Signature should still be in RAM, not yet in flash.
  const uint32_t sig_start = FIRMWARE_SLOT_SIZE - FWUP_SIGNATURE_SIZE;
  for (uint32_t i = 0; i < FWUP_SIGNATURE_SIZE; i++) {
    cr_assert_eq(firmware_b_slot[sig_start + i], 0xFF,
                 "flash sig byte %u should be 0xFF before commit", i);
  }

  // Now commit — signature should be in flash.
  cr_assert(fwup_commit_signature());
  for (uint32_t i = 0; i < FWUP_SIGNATURE_SIZE; i++) {
    cr_assert_eq(firmware_b_slot[sig_start + i], 0xDD,
                 "flash sig byte %u should be 0xDD after commit", i);
  }
}

// If fwup_finish() fails (bad properties offset), the signature must NOT be
// written to flash.  The firmware content remains but is inert without a
// valid signature.
Test(fwup, finish_failure_does_not_write_signature, .init = setup) {
  transfer_dummy_signature();

  fwpb_fwup_finish_cmd cmd = fwpb_fwup_finish_cmd_init_default;
  fwpb_fwup_finish_rsp rsp = fwpb_fwup_finish_rsp_init_default;
  cmd.app_properties_offset = 1024 * 10000;  // bad offset
  cmd.signature_offset = FIRMWARE_SLOT_SIZE - FWUP_SIGNATURE_SIZE;

  fwup_finish(&cmd, &rsp);
  cr_assert(rsp.rsp_status != fwpb_fwup_finish_rsp_fwup_finish_rsp_status_SUCCESS);

  // Signature must not have been written to flash.
  const uint32_t sig_start = FIRMWARE_SLOT_SIZE - FWUP_SIGNATURE_SIZE;
  for (uint32_t i = 0; i < FWUP_SIGNATURE_SIZE; i++) {
    cr_assert_eq(firmware_b_slot[sig_start + i], 0xFF,
                 "flash sig byte %u should be 0xFF after failed finish", i);
  }
}

// fwup_finish() without any signature transfer should fail.
Test(fwup, finish_without_signature_fails, .init = setup) {
  fwpb_fwup_finish_cmd cmd = fwpb_fwup_finish_cmd_init_default;
  fwpb_fwup_finish_rsp rsp = fwpb_fwup_finish_rsp_init_default;
  cmd.app_properties_offset = 1024;
  cmd.signature_offset = FIRMWARE_SLOT_SIZE - FWUP_SIGNATURE_SIZE;

  fwup_finish(&cmd, &rsp);
  cr_assert(rsp.rsp_status == fwpb_fwup_finish_rsp_fwup_finish_rsp_status_SIGNATURE_INVALID);
}

// fwup_start() must clear any pending signature from a previous attempt.
Test(fwup, start_clears_pending_signature, .init = setup) {
  transfer_dummy_signature();
  cr_assert(fwup_priv.has_pending_signature);

  // Start a new update — should clear the pending signature.
  start(fwpb_fwup_mode_FWUP_MODE_NORMAL, 0);

  cr_assert_eq(fwup_priv.has_pending_signature, false);
  for (uint32_t i = 0; i < FWUP_SIGNATURE_SIZE; i++) {
    cr_assert_eq(fwup_priv.pending_signature[i], 0);
  }
}

// Simulating a reboot before fwup_finish(): the signature should not be in
// flash, so the bootloader would not consider the target slot valid.
Test(fwup, reboot_before_finish_no_valid_signature, .init = setup) {
  // Transfer firmware content.
  fwpb_fwup_transfer_cmd cmd = fwpb_fwup_transfer_cmd_init_default;
  fwpb_fwup_transfer_rsp rsp = fwpb_fwup_transfer_rsp_init_default;
  cmd.sequence_id = 0;
  memset(cmd.fwup_data.bytes, 0xAB, sizeof(cmd.fwup_data.bytes));
  cmd.fwup_data.size = sizeof(cmd.fwup_data.bytes);
  cr_assert(fwup_transfer(&cmd, &rsp));

  // Transfer signature to RAM.
  transfer_dummy_signature();

  // Do NOT call fwup_finish() — simulate a reboot.
  // The signature must not be in flash.
  const uint32_t sig_start = FIRMWARE_SLOT_SIZE - FWUP_SIGNATURE_SIZE;
  for (uint32_t i = 0; i < FWUP_SIGNATURE_SIZE; i++) {
    cr_assert_eq(firmware_b_slot[sig_start + i], 0xFF, "sig byte %u must be 0xFF before finish", i);
  }
}

// Multiple signature chunks should be assembled correctly in the RAM buffer.
Test(fwup, transfer_multiple_signature_chunks, .init = setup) {
  const uint32_t sig_boundary = FIRMWARE_SLOT_SIZE - FWUP_SIGNATURE_SIZE;
  const uint32_t max_chunk_size = (uint32_t)fwup_flash_get_max_chunk_size();

  // First 32 bytes of signature.
  {
    fwpb_fwup_transfer_cmd cmd = fwpb_fwup_transfer_cmd_init_default;
    fwpb_fwup_transfer_rsp rsp = fwpb_fwup_transfer_rsp_init_default;
    const uint32_t off = sig_boundary;
    cmd.sequence_id = off / max_chunk_size;
    cmd.offset = off % max_chunk_size;
    memset(cmd.fwup_data.bytes, 0xAA, 32);
    cmd.fwup_data.size = 32;
    cr_assert(fwup_transfer(&cmd, &rsp));
  }

  // Second 32 bytes of signature.
  {
    fwpb_fwup_transfer_cmd cmd = fwpb_fwup_transfer_cmd_init_default;
    fwpb_fwup_transfer_rsp rsp = fwpb_fwup_transfer_rsp_init_default;
    const uint32_t off = sig_boundary + 32;
    cmd.sequence_id = off / max_chunk_size;
    cmd.offset = off % max_chunk_size;
    memset(cmd.fwup_data.bytes, 0xBB, 32);
    cmd.fwup_data.size = 32;
    cr_assert(fwup_transfer(&cmd, &rsp));
  }

  // Validate assembled buffer.
  cr_assert(fwup_priv.has_pending_signature);
  for (uint32_t i = 0; i < 32; i++) {
    cr_assert_eq(fwup_priv.pending_signature[i], 0xAA);
  }
  for (uint32_t i = 32; i < FWUP_SIGNATURE_SIZE; i++) {
    cr_assert_eq(fwup_priv.pending_signature[i], 0xBB);
  }

  // Flash should still be erased in the signature region.
  for (uint32_t i = 0; i < FWUP_SIGNATURE_SIZE; i++) {
    cr_assert_eq(firmware_b_slot[sig_boundary + i], 0xFF);
  }
}

// Mismatched signature_offset in finish_cmd should be rejected.
Test(fwup, finish_rejects_wrong_signature_offset, .init = setup) {
  transfer_dummy_signature();

  fwpb_fwup_finish_cmd cmd = fwpb_fwup_finish_cmd_init_default;
  fwpb_fwup_finish_rsp rsp = fwpb_fwup_finish_rsp_init_default;
  cmd.app_properties_offset = 1024;
  cmd.signature_offset = FIRMWARE_SLOT_SIZE - 128;  // wrong offset

  fwup_finish(&cmd, &rsp);
  cr_assert(rsp.rsp_status == fwpb_fwup_finish_rsp_fwup_finish_rsp_status_ERROR);
}

Test(fwup, should_not_reject_when_reset_not_pending, .init = setup_init_only) {
  fwup_mark_pending(true);
  fwup_mark_coproc_pending(true);

  cr_assert(fwup_should_reject_cmd() == false);
}

Test(fwup, should_reject_when_reset_pending, .init = setup_init_only) {
  fwup_mark_pending(true);
  fwup_mark_coproc_pending(true);
  fwup_mark_reset_pending();

  cr_assert(fwup_should_reject_cmd());
}

Test(fwup, fwup_clear_reset_pending_allows_commands, .init = setup_init_only) {
  fwup_mark_reset_pending();
  cr_assert(fwup_should_reject_cmd() == true);

  fwup_clear_reset_pending();
  cr_assert(fwup_should_reject_cmd() == false);
}

Test(fwup, fwup_init_clears_reset_pending, .init = setup_init_only) {
  fwup_mark_reset_pending();
  cr_assert(fwup_should_reject_cmd() == true);

  fwup_init(firmware_b_slot, firmware_a_slot, &firmware_b_slot[FIRMWARE_SLOT_SIZE - 64],
            FIRMWARE_SLOT_SIZE, true, false);
  cr_assert(fwup_should_reject_cmd() == false);
}

Test(fwup, fwup_in_progress_false_when_nothing_pending, .init = setup_init_only) {
  cr_assert(fwup_in_progress() == false);
}

Test(fwup, fwup_in_progress_true_when_pending, .init = setup_init_only) {
  fwup_mark_pending(true);
  cr_assert(fwup_in_progress() == true);
}

Test(fwup, fwup_in_progress_true_when_coproc_pending, .init = setup_init_only) {
  fwup_mark_coproc_pending(true);
  cr_assert(fwup_in_progress() == true);
}

// --- Staged signature tests ---

void setup_staged_sig(void) {
  init_test_logging();
  init_lfs();
  fwup_init(firmware_b_slot, firmware_a_slot, &firmware_b_slot[FIRMWARE_SLOT_SIZE - 64],
            FIRMWARE_SLOT_SIZE, true, SECURE_FALSE);
}

void teardown_staged_sig(void) {
  cleanup_lfs();
}

Test(fwup, staged_sig_write_read_roundtrip, .init = setup_staged_sig, .fini = teardown_staged_sig) {
  fwup_staged_sig_t written = {0};
  memset(written.signature, 0xAB, sizeof(written.signature));
  written.target_slot = fwpb_firmware_slot_SLOT_B;
  written.core_target_version = (fwpb_semver){.major = 1, .minor = 2, .patch = 3};
  written.uxc_target_version = (fwpb_semver){.major = 4, .minor = 5, .patch = 6};

  cr_assert(fwup_staged_sig_write(&written));
  cr_assert(fwup_staged_sig_exists());

  fwup_staged_sig_t read_back = {0};
  cr_assert(fwup_staged_sig_read(&read_back));

  cr_assert_eq(memcmp(read_back.signature, written.signature, sizeof(written.signature)), 0);
  cr_assert_eq(read_back.target_slot, written.target_slot);
  cr_assert_eq(read_back.core_target_version.major, 1);
  cr_assert_eq(read_back.core_target_version.minor, 2);
  cr_assert_eq(read_back.core_target_version.patch, 3);
  cr_assert_eq(read_back.uxc_target_version.major, 4);
  cr_assert_eq(read_back.uxc_target_version.minor, 5);
  cr_assert_eq(read_back.uxc_target_version.patch, 6);
}

Test(fwup, staged_sig_remove_clears_file, .init = setup_staged_sig, .fini = teardown_staged_sig) {
  fwup_staged_sig_t staged = {0};
  memset(staged.signature, 0xCD, sizeof(staged.signature));

  cr_assert(fwup_staged_sig_write(&staged));
  cr_assert(fwup_staged_sig_exists());

  fwup_staged_sig_remove();
  cr_assert_eq(fwup_staged_sig_exists(), false);
}

Test(fwup, staged_sig_remove_is_safe_when_absent, .init = setup_staged_sig,
     .fini = teardown_staged_sig) {
  cr_assert_eq(fwup_staged_sig_exists(), false);
  // Should not crash or error.
  fwup_staged_sig_remove();
  cr_assert_eq(fwup_staged_sig_exists(), false);
}

Test(fwup, staged_sig_read_fails_when_absent, .init = setup_staged_sig,
     .fini = teardown_staged_sig) {
  fwup_staged_sig_t staged = {0};
  cr_assert_eq(fwup_staged_sig_read(&staged), false);
}

// staged_sig_commit_to_flash is not tested here because it resolves the
// slot to a flash address via linker symbols that don't exist in the
// posix test environment.  The commit-to-flash path is covered by
// on-device edge case testing (abort injection tests).

// --- Commit signature accessor tests ---

Test(fwup, commit_signature_without_pending_fails, .init = setup_init_only) {
  fwup_init(firmware_b_slot, firmware_a_slot, &firmware_b_slot[FIRMWARE_SLOT_SIZE - 64],
            FIRMWARE_SLOT_SIZE, true, SECURE_FALSE);
  cr_assert_eq(fwup_commit_signature(), false);
}

Test(fwup, get_pending_signature_returns_null_without_pending, .init = setup_init_only) {
  fwup_init(firmware_b_slot, firmware_a_slot, &firmware_b_slot[FIRMWARE_SLOT_SIZE - 64],
            FIRMWARE_SLOT_SIZE, true, SECURE_FALSE);
  cr_assert_eq(fwup_get_pending_signature(), NULL);
}

Test(fwup, get_pending_signature_returns_data_after_transfer, .init = setup) {
  transfer_dummy_signature();
  const uint8_t* sig = fwup_get_pending_signature();
  cr_assert_neq(sig, NULL);
  for (uint32_t i = 0; i < FWUP_SIGNATURE_SIZE; i++) {
    cr_assert_eq(sig[i], 0xDD, "pending sig byte %u should be 0xDD", i);
  }
}

Test(fwup, get_target_slot_signature_returns_configured_address, .init = setup) {
  void* expected = &firmware_b_slot[FIRMWARE_SLOT_SIZE - 64];
  cr_assert_eq(fwup_get_target_slot_signature_addr(), expected);
}

Test(fwup, is_coproc_pending_tracks_state, .init = setup_init_only) {
  cr_assert_eq(fwup_is_coproc_pending(), false);
  fwup_mark_coproc_pending(true);
  cr_assert_eq(fwup_is_coproc_pending(), true);
  fwup_mark_coproc_pending(false);
  cr_assert_eq(fwup_is_coproc_pending(), false);
}
