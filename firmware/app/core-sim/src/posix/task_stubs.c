/**
 * @file task_stubs.c
 * @brief HAL/platform stubs enabling firmware tasks on POSIX
 *
 * Stub categories (in order of appearance):
 * - RTOS notifications (pthread-based)
 * - Power management
 * - Feature flags
 * - MCU reset/watchdog
 * - Biometrics
 * - Key-value store
 * - Telemetry/coredump
 * - Secure engine (SE) - including certificate and pubkey access
 * - MPU thread regions
 *
 * Real library integrations (via meson.build deps):
 * - sleep: lib/sleep/sleep.c (uses rtos_timer_t)
 */

#include "mcu_reset.h"
#include "onboarding.h"
#include "secutils.h"
#include "sim_provisioning.h"
#include "stdio_defs.h"
#include "telemetry_storage.h"
#include "wallet.pb.h"

#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

typedef struct {
  uintptr_t handle;
} rtos_thread_t;

static pthread_mutex_t notification_mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t notification_cond = PTHREAD_COND_INITIALIZER;
static volatile bool notification_pending = false;

bool rtos_notification_wait_signal(uint32_t timeout_ms) {
  pthread_mutex_lock(&notification_mutex);

  if (timeout_ms == UINT32_MAX) {
    while (!notification_pending) {
      pthread_cond_wait(&notification_cond, &notification_mutex);
    }
  } else {
    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    ts.tv_sec += timeout_ms / 1000;
    ts.tv_nsec += (timeout_ms % 1000) * 1000000;
    if (ts.tv_nsec >= 1000000000) {
      ts.tv_sec++;
      ts.tv_nsec -= 1000000000;
    }

    while (!notification_pending) {
      int result = pthread_cond_timedwait(&notification_cond, &notification_mutex, &ts);
      if (result != 0) {
        pthread_mutex_unlock(&notification_mutex);
        return false;
      }
    }
  }

  notification_pending = false;
  pthread_mutex_unlock(&notification_mutex);
  return true;
}

void rtos_notification_signal(rtos_thread_t* thread) {
  (void)thread;
  pthread_mutex_lock(&notification_mutex);
  notification_pending = true;
  pthread_cond_signal(&notification_cond);
  pthread_mutex_unlock(&notification_mutex);
}

/* Power stubs */
bool power_validate_fuel_gauge(void) {
  return true;
}

/* Called by the W1 sysinfo port's power_down path; the simulated device has no
 * power rail, so power-off leaves the process running (same as before, when
 * the posix port logged and returned). */
void power_set_retain(bool enabled) {
  (void)enabled;
}

void power_set_ldo_low_power_mode(void) {}

void power_get_battery(uint32_t* soc_millipercent, uint32_t* vcell_mv, int32_t* avg_current_ma,
                       uint32_t* cycles) {
  if (soc_millipercent)
    *soc_millipercent = 100000;
  if (vcell_mv)
    *vcell_mv = 4200;
  if (avg_current_ma)
    *avg_current_ma = -50;
  if (cycles)
    *cycles = 100;
}

/* Sleep library now provided by lib/sleep via sleep_dep in meson.build */

#define MAX_FEATURE_FLAGS 32
static bool feature_flag_values[MAX_FEATURE_FLAGS] = {0};
static bool feature_flags_initialized_flag = false;

bool feature_flags_init(void) {
  if (!feature_flags_initialized_flag) {
    memset(feature_flag_values, 0, sizeof(feature_flag_values));
    feature_flags_initialized_flag = true;
  }
  return true;
}

bool feature_flags_get(fwpb_feature_flag flag) {
  if ((uint32_t)flag < MAX_FEATURE_FLAGS) {
    return feature_flag_values[flag];
  }
  return false;
}

const bool* feature_flags_get_all(pb_size_t* len) {
  static bool all_flags[MAX_FEATURE_FLAGS];
  memcpy(all_flags, feature_flag_values, sizeof(all_flags));
  if (len)
    *len = MAX_FEATURE_FLAGS;
  return all_flags;
}

bool feature_flags_set(fwpb_feature_flag flag, bool value) {
  if ((uint32_t)flag < MAX_FEATURE_FLAGS) {
    feature_flag_values[flag] = value;
    return true;
  }
  return false;
}

bool feature_flags_set_multiple(fwpb_feature_flag_cfg* flags, pb_size_t num_flags) {
  for (pb_size_t i = 0; i < num_flags; i++) {
    if ((uint32_t)flags[i].flag < MAX_FEATURE_FLAGS) {
      feature_flag_values[flags[i].flag] = flags[i].enabled;
    }
  }
  return true;
}

static mcu_reset_reason_t stored_reset_reason = MCU_RESET_UNKNOWN;
static uint32_t rmu_cause = 0;

/* The canonical header declares this NO_RETURN, but the simulator has no MCU
 * to reset — exiting would kill the emulator on every host-initiated reset,
 * so it logs and keeps running instead. */
#ifdef __clang__
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Winvalid-noreturn"
#endif
void mcu_reset_with_reason(mcu_reset_reason_t reason) {
  LOG("mcu_reset_with_reason: %d (ignored)", reason);
  stored_reset_reason = reason;
}
#ifdef __clang__
#pragma clang diagnostic pop
#endif

void mcu_reset_set_reason(mcu_reset_reason_t reason) {
  stored_reset_reason = reason;
}

mcu_reset_reason_t mcu_reset_get_reason(void) {
  return stored_reset_reason;
}

uint32_t mcu_reset_rmu_cause_get(void) {
  return rmu_cause;
}

void mcu_reset_rmu_clear(void) {
  rmu_cause = 0;
}

/* Watchdog stubs - mcu_wdog_feed is called from sysinfo_task.c
 * wdog_feed_callback */
void mcu_wdog_init(void) {}

void mcu_wdog_feed(void) {}

/* Telemetry stubs - the simulator has no telemetry flash region. */
void telemetry_init(telemetry_api_t api) {
  (void)api;
}

uint8_t* telemetry_event_storage_get(void) {
  return NULL;
}

uint8_t* telemetry_log_storage_get(void) {
  return NULL;
}

bool telemetry_coredump_save(void) {
  return false;
}

uint32_t telemetry_coredump_count(void) {
  return 0;
}

bool telemetry_coredump_read_fragment(uint32_t offset, fwpb_coredump_fragment* fragment) {
  (void)offset;
  (void)fragment;
  return false;
}

// Include SE types from the crypto library for API compatibility
#include "secure_engine.h"

sl_status_t se_get_secinfo(se_info_t* info) {
  if (!info)
    return SL_STATUS_FAIL;
  memset(info, 0, sizeof(*info));
  info->version = 0x02010100;
  info->otp_version = 1;

  if (sim_is_provisioned()) {
    const uint8_t* serial = sim_get_serial();
    if (serial) {
      memcpy(info->serial + 8, serial, 8);
    }
  } else {
    memcpy(info->serial + 8, "POSIXSE0", 8);
  }

  info->otp.enable_secure_boot = true;
  info->otp.verify_secure_boot_certificate = true;
  info->se_status.secure_boot_enabled = true;

  return SL_STATUS_OK;
}

sl_status_t se_read_cert(sl_se_cert_type_t kind, uint8_t* cert, uint16_t* size) {
  if (sim_is_provisioned()) {
    size_t len = 0;
    const uint8_t* data = NULL;

    switch (kind) {
      case SL_SE_CERT_BATCH:
        data = sim_get_batch_cert(&len);
        break;
      case SL_SE_CERT_DEVICE_SE:
      case SL_SE_CERT_DEVICE_HOST:
        data = sim_get_device_cert(&len);
        break;
      default:
        if (size)
          *size = 0;
        return SL_STATUS_INVALID_PARAMETER;
    }

    if (data && len > SIM_CERT_MAX_SIZE) {
      if (size)
        *size = 0;
      return SL_STATUS_FAIL;
    }

    if (data && len > 0 && cert) {
      memcpy(cert, data, len);
      if (size)
        *size = (uint16_t)len;
      return SL_STATUS_OK;
    }
  }

  (void)kind;
  (void)cert;
  if (size)
    *size = 0;
  return SL_STATUS_NOT_AVAILABLE;
}

static void derive_pubkey(const uint8_t* device_pubkey, uint8_t* out, uint8_t xor_mask) {
  memcpy(out, device_pubkey, 64);
  if (xor_mask != 0) {
    out[0] ^= xor_mask;
  }
}

static uint8_t key_type_to_xor_mask(sl_se_device_key_type_t kind) {
  switch (kind) {
    case SL_SE_KEY_TYPE_IMMUTABLE_BOOT:
      return 0x01;
    case SL_SE_KEY_TYPE_IMMUTABLE_AUTH:
      return 0x02;
    case SL_SE_KEY_TYPE_IMMUTABLE_ATTESTATION:
      return 0x00;
    case SL_SE_KEY_TYPE_IMMUTABLE_SE_ATTESTATION:
      return 0x03;
    default:
      return 0xFF;
  }
}

sl_status_t se_read_pubkeys(se_pubkeys_t* pubkeys) {
  if (!sim_is_provisioned() || !pubkeys) {
    return SL_STATUS_NOT_AVAILABLE;
  }

  const uint8_t* device_pubkey = sim_get_device_pubkey();
  if (!device_pubkey) {
    return SL_STATUS_NOT_AVAILABLE;
  }

  if (pubkeys->boot)
    derive_pubkey(device_pubkey, pubkeys->boot, 0x01);
  if (pubkeys->auth)
    derive_pubkey(device_pubkey, pubkeys->auth, 0x02);
  if (pubkeys->attestation)
    derive_pubkey(device_pubkey, pubkeys->attestation, 0x00);
  if (pubkeys->se_attestation)
    derive_pubkey(device_pubkey, pubkeys->se_attestation, 0x03);

  return SL_STATUS_OK;
}

sl_status_t se_read_pubkey(sl_se_device_key_type_t kind, uint8_t* pubkey, uint32_t size) {
  if (!sim_is_provisioned() || !pubkey || size < 64) {
    return SL_STATUS_NOT_AVAILABLE;
  }

  const uint8_t* device_pubkey = sim_get_device_pubkey();
  if (!device_pubkey) {
    return SL_STATUS_NOT_AVAILABLE;
  }

  uint8_t xor_mask = key_type_to_xor_mask(kind);
  if (xor_mask == 0xFF) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  derive_pubkey(device_pubkey, pubkey, xor_mask);
  return SL_STATUS_OK;
}

sl_status_t se_get_secure_boot_config(secure_boot_config_t* config) {
  if (config)
    *config = SECURE_BOOT_CONFIG_DEV;
  return SL_STATUS_OK;
}

sl_status_t se_get_status(sl_se_status_t* se_status) {
  if (!se_status)
    return SL_STATUS_INVALID_PARAMETER;
  memset(se_status, 0, sizeof(*se_status));
  se_status->secure_boot_enabled = true;
  return SL_STATUS_OK;
}

sl_status_t se_read_serial(uint8_t serial[SE_SERIAL_SIZE]) {
  if (!serial)
    return SL_STATUS_INVALID_PARAMETER;
  memset(serial, 0, SE_SERIAL_SIZE);

  if (sim_is_provisioned()) {
    const uint8_t* sim_serial = sim_get_serial();
    if (sim_serial) {
      memcpy(serial + SE_ACTUAL_SERIAL_START, sim_serial, SE_ACTUAL_SERIAL_SIZE);
      return SL_STATUS_OK;
    }
  }

  // Default: use "POSIXSE0"
  memcpy(serial + SE_ACTUAL_SERIAL_START, "POSIXSE0", SE_ACTUAL_SERIAL_SIZE);
  return SL_STATUS_OK;
}

/* rtos_thread_is_privileged() is provided as a no-op macro by rtos_mpu.h
 * for non-embedded builds. */

void unlock_perform_wipe_state(void) {
  onboarding_wipe_state();
}

/* On embedded targets `active_slot` is a linker-script symbol whose *address*
 * encodes the active slot (fwpb_firmware_slot_SLOT_A == 1). Emulate that with
 * absolute symbols here:
 * - `posix_active_slot` satisfies core-sim sources, which are compiled with
 *   -Dactive_slot=posix_active_slot.
 * - `active_slot` satisfies library objects (e.g. lib/metadata) that are
 *   compiled without that define. */
#ifdef __APPLE__
__asm__(".globl _posix_active_slot\n.set _posix_active_slot, 1");
__asm__(".globl _active_slot\n.set _active_slot, 1");
#else
__asm__(".globl posix_active_slot\n.set posix_active_slot, 1");
__asm__(".globl active_slot\n.set active_slot, 1");
#endif

/* Metadata flash pages: on embedded these are linker-script symbols pointing
 * at dedicated flash pages, with the *_size symbols' addresses encoding the
 * page size. Provide RAM-backed pages (zero-filled, so metadata parsing
 * reports METADATA_INVALID and callers fall back gracefully) and absolute
 * size symbols for lib/metadata. */
#define POSIX_METADATA_PAGE_SIZE 4096
uint8_t bl_metadata_page[POSIX_METADATA_PAGE_SIZE];
uint8_t app_a_metadata_page[POSIX_METADATA_PAGE_SIZE];
uint8_t app_b_metadata_page[POSIX_METADATA_PAGE_SIZE];
#ifdef __APPLE__
__asm__(".globl _bl_metadata_size\n.set _bl_metadata_size, 4096");
__asm__(".globl _app_a_metadata_size\n.set _app_a_metadata_size, 4096");
__asm__(".globl _app_b_metadata_size\n.set _app_b_metadata_size, 4096");
#else
__asm__(".globl bl_metadata_size\n.set bl_metadata_size, 4096");
__asm__(".globl app_a_metadata_size\n.set app_a_metadata_size, 4096");
__asm__(".globl app_b_metadata_size\n.set app_b_metadata_size, 4096");
#endif

#include "power.h"

power_config_t power_config = {0};

/* GPIO no-ops - the simulator has no pins to drive. */
void mcu_gpio_configure(const mcu_gpio_config_t* gpio, const bool output_set) {
  (void)gpio;
  (void)output_set;
}

void mcu_gpio_set(const mcu_gpio_config_t* gpio) {
  (void)gpio;
}

void mcu_gpio_clear(const mcu_gpio_config_t* gpio) {
  (void)gpio;
}

#include "rtos_mpu.h"

rtos_thread_mpu_t _fwup_thread_regions = {0};
rtos_thread_mpu_t _sysinfo_thread_regions = {0};
rtos_thread_mpu_t _key_manager_thread_regions = {0};
rtos_thread_mpu_t _crypto_thread_regions = {0};
rtos_thread_mpu_t _fs_mount_task_regions = {0};

/* Confirmation manager now provided by lib/confirmation via confirmation_dep in meson.build */
