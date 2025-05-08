#include "application_properties.h"
#include "arithmetic.h"
#include "attributes.h"
#include "bd/lfs_emubd.h"
#include "bitlog.h"
#include "criterion_test_utils.h"
#include "fff.h"
#include "fwup.h"
#include "hex.h"
#include "rtos.h"
#include "security_config.h"

#include <criterion/criterion.h>
#include <criterion/new/assert.h>

DEFINE_FFF_GLOBALS;
FAKE_VOID_FUNC(secure_glitch_random_delay);
FAKE_VALUE_FUNC(bool, bd_error_str, char*, const size_t, const int);
FAKE_VALUE_FUNC(int, bd_erase_all);
FAKE_VALUE_FUNC(lfs_t*, bd_mount);
FAKE_VOID_FUNC(rtos_event_group_create, rtos_event_group_t*);
FAKE_VALUE_FUNC(uint32_t, rtos_event_group_set_bits, rtos_event_group_t*, const uint32_t);
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

static void start(void) {
  fwpb_fwup_start_cmd cmd = fwpb_fwup_start_cmd_init_default;
  cmd.mode = fwpb_fwup_mode_FWUP_MODE_NORMAL;
  fwpb_fwup_start_rsp rsp = fwpb_fwup_start_rsp_init_default;
  cr_assert(fwup_start(&cmd, &rsp));
  cr_assert(rsp.rsp_status == fwpb_fwup_start_rsp_fwup_start_rsp_status_SUCCESS);
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

void setup(void) {
  bitlog_init((bitlog_api_t){
    .timestamp_cb = timestamp,
  });
  fwup_init(firmware_b_slot, firmware_a_slot, &firmware_b_slot[FIRMWARE_SLOT_SIZE - 64],
            FIRMWARE_SLOT_SIZE, true);
  start();
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

Test(fwup, transfer_bad_offset, .init = setup) {
  fwpb_fwup_transfer_cmd cmd = fwpb_fwup_transfer_cmd_init_default;
  fwpb_fwup_transfer_rsp rsp = fwpb_fwup_transfer_rsp_init_default;

  cmd.offset = 450 * 10000;  // Arbitrarily too-large number
  cmd.fwup_data.size = FIELD_SIZEOF(fwpb_fwup_transfer_cmd_fwup_data_t, bytes);

  cr_assert(fwup_transfer(&cmd, &rsp) == false);
  cr_assert(rsp.rsp_status == fwpb_fwup_transfer_rsp_fwup_transfer_rsp_status_ERROR);
}

Test(fwup, finish_bad_properties_offset, .init = setup) {
  fwpb_fwup_finish_cmd cmd = fwpb_fwup_finish_cmd_init_default;
  fwpb_fwup_finish_rsp rsp = fwpb_fwup_finish_rsp_init_default;

  cmd.app_properties_offset = 1024 * 10000;  // Arbitrarily too-large number
  cmd.signature_offset = FIRMWARE_SLOT_SIZE - 64;

  fwup_finish(&cmd, &rsp);
  cr_assert(rsp.rsp_status == fwpb_fwup_finish_rsp_fwup_finish_rsp_status_ERROR);
}

Test(fwup, finish_bad_signature_offset, .init = setup) {
  fwpb_fwup_finish_cmd cmd = fwpb_fwup_finish_cmd_init_default;
  fwpb_fwup_finish_rsp rsp = fwpb_fwup_finish_rsp_init_default;

  cmd.app_properties_offset = 1024;
  cmd.signature_offset = 800 * 10000;  // Arbitrarily too-large number

  fwup_finish(&cmd, &rsp);
  cr_assert(rsp.rsp_status == fwpb_fwup_finish_rsp_fwup_finish_rsp_status_ERROR);
}
