#include "FuzzedDataProvider.h"
extern "C" {
#include "application_properties.h"
#include "arithmetic.h"
#include "attributes.h"
#include "bd/lfs_emubd.h"
#include "bitlog.h"
#include "fff.h"
#include "fwup.h"
#include "hex.h"
#include "rtos.h"
#include "security_config.h"
#include "secutils.h"
#include "wallet.pb.h"

DEFINE_FFF_GLOBALS;

FAKE_VOID_FUNC(secure_glitch_random_delay);
FAKE_VOID_FUNC(refresh_auth);

FAKE_VALUE_FUNC(int, bd_erase_all);
FAKE_VALUE_FUNC(lfs_t*, bd_mount);
FAKE_VOID_FUNC(rtos_event_group_create, rtos_event_group_t*);
FAKE_VOID_FUNC(rtos_semaphore_create, rtos_semaphore_t*);

FAKE_VOID_FUNC(rtos_timer_create_static, rtos_timer_t*, rtos_timer_callback_t);
FAKE_VOID_FUNC(rtos_timer_start, rtos_timer_t*, uint32_t);
FAKE_VOID_FUNC(rtos_timer_stop, rtos_timer_t*);

secure_bool_t onboarding_complete(void) {
  return SECURE_TRUE;
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
secure_bool_t addr_in_flash(uintptr_t UNUSED(addr)) {
  return SECURE_TRUE;
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
bool bd_error_str(char*, const size_t, const int) {
  return true;
}
uint32_t rtos_event_group_set_bits(rtos_event_group_t*, const uint32_t) {
  return 0;
}
uint32_t rtos_event_group_clear_bits(rtos_event_group_t*, const uint32_t) {
  return 0;
}
uint32_t rtos_event_group_wait_bits(rtos_event_group_t*, const uint32_t, const bool, const bool,
                                    uint32_t) {
  return 0;
}
bool bio_fingerprint_exists(void) {
  return true;
}

void rtos_mutex_create(rtos_mutex_t* UNUSED(mutex)) {}
typedef void (*f_cb)(void*);
FAKE_VOID_FUNC(rtos_thread_create_static, rtos_thread_t*, f_cb, const char*, void*,
               rtos_thread_priority_t, uint32_t*, uint32_t, StaticTask_t*, rtos_thread_mpu_t);
FAKE_VOID_FUNC(rtos_thread_delete, rtos_thread_t*);
security_config_t security_config = {
  .is_production = SECURE_FALSE,
  .biometrics_mac_key = {0},
  .fwup_delta_patch_pubkey = {0},
};

rtos_thread_mpu_t _fs_mount_task_regions;
}

#include "application_properties.h"

#include <stddef.h>
#include <stdint.h>

enum {
  kStart,
  kTransfer,
  kFinish,
};

constexpr uint32_t kFirmwareSlotSize = 632 * 1024;

uint8_t firmware_a_slot[kFirmwareSlotSize] = {0};
uint8_t firmware_b_slot[kFirmwareSlotSize] = {0};
uint8_t __attribute__((aligned(8192))) firmware_b_signature[8192] = {0};

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

static void fuzz_start(FuzzedDataProvider& data) {
  fwpb_fwup_start_rsp rsp = fwpb_fwup_start_rsp_init_default;
  fwpb_fwup_start_cmd cmd = fwpb_fwup_start_cmd_init_default;
  cmd.mode =
    (fwpb_fwup_mode)data.ConsumeIntegralInRange<int>(_fwpb_fwup_mode_MIN, _fwpb_fwup_mode_MAX);
  cmd.patch_size = data.ConsumeIntegral<uint32_t>();
  fwup_start(&cmd, &rsp);
}

static void fuzz_transfer(FuzzedDataProvider& data) {
  fwpb_fwup_transfer_cmd cmd = fwpb_fwup_transfer_cmd_init_default;
  fwpb_fwup_transfer_rsp rsp = fwpb_fwup_transfer_rsp_init_default;

  cmd.sequence_id = data.ConsumeIntegral<uint32_t>();

  std::vector<uint8_t> bytes = data.ConsumeBytes<uint8_t>(
    data.ConsumeIntegralInRange<uint16_t>(0, sizeof(cmd.fwup_data.bytes)));
  memcpy(&cmd.fwup_data.bytes, bytes.data(), bytes.size());

  if (data.ConsumeBool()) {
    cmd.fwup_data.size = bytes.size();
  } else {
    cmd.fwup_data.size = data.ConsumeIntegralInRange<uint16_t>(0, sizeof(cmd.fwup_data.bytes));
  }

  cmd.offset = data.ConsumeIntegral<uint32_t>();

  fwup_transfer(&cmd, &rsp);
}

static void fuzz_finish(FuzzedDataProvider& data) {
  fwpb_fwup_finish_cmd cmd = fwpb_fwup_finish_cmd_init_default;
  fwpb_fwup_finish_rsp rsp = fwpb_fwup_finish_rsp_init_default;

  cmd.app_properties_offset = data.ConsumeIntegral<uint32_t>();
  cmd.signature_offset = data.ConsumeIntegral<uint32_t>();

  fwup_finish(&cmd, &rsp);
}

static uint32_t timestamp(void) {
  return 0;
}

// NOTE: would be nice to use libprotobuf-mutator, but that requires C++ protos.
extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  FuzzedDataProvider fuzzed_data(data, size);

  fwup_init((uint32_t*)firmware_b_slot, (uint32_t*)firmware_a_slot, firmware_b_signature,
            kFirmwareSlotSize, true);
  bitlog_init((bitlog_api_t){
    .timestamp_cb = timestamp,
  });

  if (fuzzed_data.remaining_bytes() > 0) {
    uint32_t cmd = fuzzed_data.ConsumeIntegralInRange<uint16_t>(kStart, kFinish);
    switch (cmd) {
      case kStart:
        fuzz_start(fuzzed_data);
        break;
      case kTransfer:
        fuzz_transfer(fuzzed_data);
        break;
      case kFinish:
        fuzz_finish(fuzzed_data);
        break;
    }
  }
  return 0;
}
