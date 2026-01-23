#include "sysinfo_task.h"

#include "attributes.h"
#include "auth.h"
#include "bio.h"
#include "bitlog.h"
#include "feature_flags.h"
#include "filesystem.h"
#include "ipc.h"
#include "kv.h"
#include "log.h"
#include "mcu_reset.h"
#include "mcu_wdog.h"
#include "metadata.h"
#include "pb_decode.h"
#include "pb_encode.h"
#include "power.h"
#include "proto_helpers.h"
#include "rtos.h"
#include "rtos_notification.h"
#include "secure_engine.h"
#include "secutils.h"
#include "sleep.h"
#include "sysevent.h"
#include "sysinfo.h"
#include "sysinfo_task_impl.h"
#include "telemetry_storage.h"
#include "ui_messaging.h"
#include "wallet.pb.h"

#include <stdbool.h>

#define WDOG_REFRESH_MS (1000)

extern char active_slot[];
extern power_config_t power_config;

static struct {
  rtos_queue_t* queue;
  rtos_timer_t wdog_timer;
  uint32_t power_timeout_ms;
  uint32_t wdog_timer_refresh_ms;
  platform_hwrev_t hwrev;
  rtos_timer_t cap_touch_cal_timer;
  rtos_mutex_t kv_mutex;
} sysinfo_task_priv SHARED_TASK_DATA = {
  .queue = NULL,
  .wdog_timer = {0},
  .wdog_timer_refresh_ms = WDOG_REFRESH_MS,
  .hwrev = 0,
  .cap_touch_cal_timer = {0},
  .kv_mutex = {0},
};

static bool kv_mutex_lock(void) {
  return rtos_mutex_lock(&sysinfo_task_priv.kv_mutex);
}

static bool kv_mutex_unlock(void) {
  return rtos_mutex_unlock(&sysinfo_task_priv.kv_mutex);
}

static void power_system_down_callback(rtos_timer_handle_t UNUSED(timer)) {
  sysinfo_task_port_prepare_sleep_and_power_down();
}

static void wdog_feed_callback(rtos_timer_handle_t UNUSED(timer)) {
  mcu_wdog_feed();
  rtos_timer_restart(&sysinfo_task_priv.wdog_timer);
}

void copy_metadata_to_proto(metadata_t* metadata, fwpb_firmware_metadata* proto) {
  strncpy(proto->git_id, metadata->git.id, METADATA_GIT_STR_MAX_LEN);
  strncpy(proto->git_branch, metadata->git.branch, METADATA_GIT_STR_MAX_LEN);

  proto->has_version = true;
  proto->version.major = metadata->version.major;
  proto->version.minor = metadata->version.minor;
  proto->version.patch = metadata->version.patch;

  strncpy(proto->build, metadata->build, METADATA_BUILD_STR_MAX_LEN);

  proto->timestamp = metadata->timestamp;
  memcpy(proto->hash.bytes, metadata->sha1hash, METADATA_HASH_LENGTH);
  proto->hash.size = METADATA_HASH_LENGTH;

  strncpy(proto->hw_revision, metadata->hardware_revision, METADATA_HW_REV_STR_MAX_LEN);
}

void handle_meta_cmd(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  if (cmd->msg.meta_cmd.mcu_role != fwpb_mcu_role_MCU_ROLE_CORE) {
    sysinfo_task_request_coproc_metadata(cmd);
    return;
  }

  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_meta_rsp_tag;

  metadata_t metadata = {0};
  rsp->msg.meta_rsp.meta_bl.valid = metadata_get(META_TGT_BL, &metadata) == METADATA_VALID;
  rsp->msg.meta_rsp.has_meta_bl = true;
  copy_metadata_to_proto(&metadata, &rsp->msg.meta_rsp.meta_bl);
  memset(&metadata, 0,
         sizeof(metadata));  // Ensure it's clear when metadata isn't present by explicitly zeroing

  rsp->msg.meta_rsp.meta_slot_a.valid = metadata_get(META_TGT_APP_A, &metadata) == METADATA_VALID;
  rsp->msg.meta_rsp.has_meta_slot_a = true;
  copy_metadata_to_proto(&metadata, &rsp->msg.meta_rsp.meta_slot_a);
  memset(&metadata, 0, sizeof(metadata));

  rsp->msg.meta_rsp.meta_slot_b.valid = metadata_get(META_TGT_APP_B, &metadata) == METADATA_VALID;
  rsp->msg.meta_rsp.has_meta_slot_b = true;
  copy_metadata_to_proto(&metadata, &rsp->msg.meta_rsp.meta_slot_b);
  memset(&metadata, 0, sizeof(metadata));

  rsp->msg.meta_rsp.active_slot = (fwpb_firmware_slot)active_slot;

  rsp->msg.meta_rsp.rsp_status = rsp->msg.meta_rsp.meta_bl.valid ||
                                     rsp->msg.meta_rsp.meta_slot_a.valid ||
                                     rsp->msg.meta_rsp.meta_slot_b.valid
                                   ? fwpb_meta_rsp_meta_rsp_status_SUCCESS
                                   : fwpb_meta_rsp_meta_rsp_status_ERROR;

  proto_send_rsp(cmd, rsp);
}

void handle_device_id_cmd(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_device_id_rsp_tag;

  _Static_assert(SYSINFO_SERIAL_NUMBER_LENGTH == sizeof(rsp->msg.device_id_rsp.assy_serial) - 1,
                 "wrong serial size");

  _Static_assert(SYSINFO_SERIAL_NUMBER_LENGTH == sizeof(rsp->msg.device_id_rsp.mlb_serial) - 1,
                 "wrong serial size");

  uint32_t length = 0;
  rsp->msg.device_id_rsp.assy_serial_valid =
    sysinfo_assy_serial_read((char*)rsp->msg.device_id_rsp.assy_serial, &length);
  rsp->msg.device_id_rsp.mlb_serial_valid =
    sysinfo_mlb_serial_read((char*)rsp->msg.device_id_rsp.mlb_serial, &length);

  proto_send_rsp(cmd, rsp);
}

NO_OPTIMIZE void handle_wipe_state(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_wipe_state_rsp_tag;
  rsp->msg.wipe_state_rsp.rsp_status = fwpb_wipe_state_rsp_wipe_state_rsp_status_ERROR;

  ipc_send_empty(key_manager_port, IPC_KEY_MANAGER_REMOVE_WALLET_STATE);
  rsp->msg.wipe_state_rsp.rsp_status = fwpb_wipe_state_rsp_wipe_state_rsp_status_SUCCESS;

  SECURE_DO({ deauthenticate(); });

  // Resume the pre-onboarding rest animation after state is wiped.
  UI_SET_IDLE_STATE(UI_EVENT_IDLE);

  proto_send_rsp(cmd, rsp);
}

void handle_fuel_cmd(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_fuel_rsp_tag;

  rsp->msg.fuel_rsp.valid = power_validate_fuel_gauge();
  if (rsp->msg.fuel_rsp.valid) {
    uint32_t unused;
    // This command is outdated, prefer device info.
    power_get_battery(&rsp->msg.fuel_rsp.repsoc, &rsp->msg.fuel_rsp.vcell, (int32_t*)&unused,
                      &unused);
  }
  proto_send_rsp(cmd, rsp);
}

void handle_coredump_get(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_coredump_get_rsp_tag;

  switch (cmd->msg.coredump_get_cmd.type) {
    case fwpb_coredump_get_cmd_coredump_get_type_COUNT:
      rsp->msg.coredump_get_rsp.coredump_count = telemetry_coredump_count();
      rsp->msg.coredump_get_rsp.rsp_status = fwpb_coredump_get_rsp_coredump_get_rsp_status_SUCCESS;
      break;
    case fwpb_coredump_get_cmd_coredump_get_type_COREDUMP:
      if (telemetry_coredump_read_fragment(cmd->msg.coredump_get_cmd.offset,
                                           &rsp->msg.coredump_get_rsp.coredump_fragment)) {
        rsp->msg.coredump_get_rsp.has_coredump_fragment = true;
        rsp->msg.coredump_get_rsp.rsp_status =
          fwpb_coredump_get_rsp_coredump_get_rsp_status_SUCCESS;
      } else {
        rsp->msg.coredump_get_rsp.rsp_status = fwpb_coredump_get_rsp_coredump_get_rsp_status_ERROR;
      }
      break;
    default:
      rsp->msg.coredump_get_rsp.rsp_status = fwpb_coredump_get_rsp_coredump_get_rsp_status_ERROR;
      goto out;
  }

out:
  proto_send_rsp(cmd, rsp);
}

void handle_events_get(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  uint32_t written = 0;

  rsp->which_msg = fwpb_wallet_rsp_events_get_rsp_tag;

  rsp->msg.events_get_rsp.has_fragment = true;
  rsp->msg.events_get_rsp.fragment.remaining_size =
    bitlog_drain(rsp->msg.events_get_rsp.fragment.data.bytes,
                 sizeof(rsp->msg.events_get_rsp.fragment.data.bytes), &written);
  rsp->msg.events_get_rsp.fragment.data.size = written;
  rsp->msg.events_get_rsp.rsp_status = fwpb_events_get_rsp_events_get_rsp_status_SUCCESS;

  rsp->msg.events_get_rsp.version = EVENT_STORAGE_VERSION;

  proto_send_rsp(cmd, rsp);
}

// TODO: Deprecate this.
void handle_telemetry_id_get(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  fwpb_telemetry_id_get_rsp* telem_id = &rsp->msg.telemetry_id_get_rsp;
  rsp->which_msg = fwpb_wallet_rsp_telemetry_id_get_rsp_tag;

  _Static_assert(SYSINFO_SERIAL_NUMBER_LENGTH == sizeof(telem_id->serial) - 1, "wrong serial size");

  uint32_t length = 0;
  if (!sysinfo_assy_serial_read((char*)telem_id->serial, &length)) {
    goto error;
  }

  metadata_t metadata = {0};
  char slot;
  switch ((uint32_t)active_slot) {
    case fwpb_firmware_slot_SLOT_A:
      if (metadata_get(META_TGT_APP_A, &metadata) != METADATA_VALID) {
        goto error;
      }
      slot = 'a';
      break;
    case fwpb_firmware_slot_SLOT_B:
      if (metadata_get(META_TGT_APP_B, &metadata) != METADATA_VALID) {
        goto error;
      }
      slot = 'b';
      break;
    default:
      goto error;
  }

  telem_id->has_version = true;
  telem_id->version.major = metadata.version.major;
  telem_id->version.minor = metadata.version.minor;
  telem_id->version.patch = metadata.version.patch;

  // W-2276/use-prod-firmware-variant
  sprintf(telem_id->sw_type, "app-%c-dev", slot);

  // Memfault does not expect the w1a- prefix, so remove it here.
  const int prefix_len = 3;
  strncpy(telem_id->hw_revision, &metadata.hardware_revision[prefix_len + 1],
          METADATA_HW_REV_STR_MAX_LEN - prefix_len);

  rsp->msg.telemetry_id_get_rsp.rsp_status =
    fwpb_telemetry_id_get_rsp_telemetry_id_get_rsp_status_SUCCESS;

  goto out;

error:
  rsp->msg.telemetry_id_get_rsp.rsp_status =
    fwpb_telemetry_id_get_rsp_telemetry_id_get_rsp_status_ERROR;
out:
  proto_send_rsp(cmd, rsp);
}

void handle_feature_flags_get(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_feature_flags_get_rsp_tag;

  bool* flags = feature_flags_get_all(&rsp->msg.feature_flags_get_rsp.flags_count);

  for (fwpb_feature_flag i = 0; i < rsp->msg.feature_flags_get_rsp.flags_count; i++) {
    rsp->msg.feature_flags_get_rsp.flags[i].flag = i;
    rsp->msg.feature_flags_get_rsp.flags[i].enabled = flags[i];
  }

  rsp->msg.feature_flags_get_rsp.rsp_status =
    fwpb_feature_flags_get_rsp_feature_flags_get_rsp_status_SUCCESS;

  proto_send_rsp(cmd, rsp);
}

void handle_feature_flags_set(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_feature_flags_set_rsp_tag;

  rsp->msg.feature_flags_set_rsp.rsp_status =
    feature_flags_set_multiple(cmd->msg.feature_flags_set_cmd.flags,
                               cmd->msg.feature_flags_set_cmd.flags_count)
      ? fwpb_feature_flags_set_rsp_feature_flags_set_rsp_status_SUCCESS
      : fwpb_feature_flags_set_rsp_feature_flags_set_rsp_status_ERROR;

  proto_send_rsp(cmd, rsp);
}

void handle_secinfo_get(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_secinfo_get_rsp_tag;

  sl_se_command_context_t cmd_ctx = {0};
  if (sl_se_init_command_context(&cmd_ctx) != SL_STATUS_OK) {
    goto out;
  }

  se_info_t se_info = {0};
  if (se_get_secinfo(&se_info) != SL_STATUS_OK) {
    rsp->msg.secinfo_get_rsp.rsp_status = fwpb_secinfo_get_rsp_secinfo_rsp_status_OTP_READ_FAIL;
    LOGE("Failed to get secinfo; is the device fused?");
    goto out;
  }

  rsp->msg.secinfo_get_rsp.has_se_info = true;

  fwpb_se_info* pb_se_info = &rsp->msg.secinfo_get_rsp.se_info;

  pb_se_info->version = se_info.version;
  pb_se_info->otp_version = se_info.otp_version;
  pb_se_info->serial.size = sizeof(se_info.serial);
  memcpy(pb_se_info->serial.bytes, se_info.serial, sizeof(se_info.serial));

  pb_se_info->enable_secure_boot = se_info.otp.enable_secure_boot;
  pb_se_info->verify_secure_boot_certificate = se_info.otp.verify_secure_boot_certificate;
  pb_se_info->enable_anti_rollback = se_info.otp.enable_anti_rollback;
  pb_se_info->secure_boot_page_lock_narrow = se_info.otp.secure_boot_page_lock_narrow;
  pb_se_info->secure_boot_page_lock_full = se_info.otp.secure_boot_page_lock_full;
  pb_se_info->tamper_levels.size = sizeof(se_info.otp.tamper_levels);
  memcpy(pb_se_info->tamper_levels.bytes, se_info.otp.tamper_levels,
         sizeof(se_info.otp.tamper_levels));
  pb_se_info->tamper_filter_period = se_info.otp.tamper_filter_period;
  pb_se_info->tamper_filter_threshold = se_info.otp.tamper_filter_threshold;
  pb_se_info->tamper_flags = se_info.otp.tamper_flags;
  pb_se_info->tamper_reset_threshold = se_info.otp.tamper_reset_threshold;

  pb_se_info->boot_status = se_info.se_status.boot_status;
  pb_se_info->se_fw_version = se_info.se_status.se_fw_version;
  pb_se_info->host_fw_version = se_info.se_status.host_fw_version;

  pb_se_info->device_erase_enabled = se_info.se_status.debug_status.device_erase_enabled;
  pb_se_info->secure_debug_enabled = se_info.se_status.debug_status.secure_debug_enabled;
  pb_se_info->debug_port_lock_applied = se_info.se_status.debug_status.debug_port_lock_applied;
  pb_se_info->debug_port_lock_state = se_info.se_status.debug_status.debug_port_lock_state;
  pb_se_info->debug_options_config.size = sizeof(se_info.se_status.debug_status.options_config);
  memcpy(pb_se_info->debug_options_config.bytes, &se_info.se_status.debug_status.options_config,
         sizeof(se_info.se_status.debug_status.options_config));
  pb_se_info->debug_options_config.size = sizeof(se_info.se_status.debug_status.options_config);
  memcpy(pb_se_info->debug_options_state.bytes, &se_info.se_status.debug_status.options_state,
         sizeof(se_info.se_status.debug_status.options_state));

  pb_se_info->secure_boot_enabled = se_info.se_status.secure_boot_enabled;
  pb_se_info->tamper_status = se_info.se_status.tamper_status;
  pb_se_info->tamper_status_raw = se_info.se_status.tamper_status_raw;

  rsp->msg.secinfo_get_rsp.rsp_status = fwpb_secinfo_get_rsp_secinfo_rsp_status_SUCCESS;

out:
  proto_send_rsp(cmd, rsp);
}

void handle_cert_get(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_cert_get_rsp_tag;

  sl_se_command_context_t cmd_ctx = {0};
  if (sl_se_init_command_context(&cmd_ctx) != SL_STATUS_OK) {
    goto out;
  }

  if (se_read_cert(cmd->msg.cert_get_cmd.kind, rsp->msg.cert_get_rsp.cert.bytes,
                   &rsp->msg.cert_get_rsp.cert.size) != SL_STATUS_OK) {
    LOGE("Failed to get certs");
    rsp->msg.cert_get_rsp.rsp_status = fwpb_cert_get_rsp_cert_get_rsp_status_CERT_READ_FAIL;
    goto out;
  }

  rsp->msg.cert_get_rsp.rsp_status = fwpb_cert_get_rsp_cert_get_rsp_status_SUCCESS;

out:
  proto_send_rsp(cmd, rsp);
}

void handle_pubkeys_get(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_pubkeys_get_rsp_tag;

  se_pubkeys_t pubkeys = {
    .boot = rsp->msg.pubkeys_get_rsp.boot_pubkey.bytes,
    .auth = rsp->msg.pubkeys_get_rsp.auth_pubkey.bytes,
    .attestation = rsp->msg.pubkeys_get_rsp.attesation_pubkey.bytes,
    .se_attestation = rsp->msg.pubkeys_get_rsp.se_attestation_pubkey.bytes,
  };

  if (se_read_pubkeys(&pubkeys) != SL_STATUS_OK) {
    LOGE("Failed to get pubkeys");
    rsp->msg.pubkeys_get_rsp.rsp_status =
      fwpb_pubkeys_get_rsp_pubkeys_get_rsp_status_PUBKEYS_READ_FAIL;
    goto out;
  }

  rsp->msg.pubkeys_get_rsp.boot_pubkey.size = SE_PUBKEY_SIZE;
  rsp->msg.pubkeys_get_rsp.auth_pubkey.size = SE_PUBKEY_SIZE;
  rsp->msg.pubkeys_get_rsp.attesation_pubkey.size = SE_PUBKEY_SIZE;
  rsp->msg.pubkeys_get_rsp.se_attestation_pubkey.size = SE_PUBKEY_SIZE;

  rsp->msg.pubkeys_get_rsp.rsp_status = fwpb_pubkeys_get_rsp_pubkeys_get_rsp_status_SUCCESS;

out:
  proto_send_rsp(cmd, rsp);
}

void handle_pubkey_get(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_pubkey_get_rsp_tag;
  rsp->msg.pubkey_get_rsp.rsp_status = fwpb_pubkey_get_rsp_pubkey_get_rsp_status_PUBKEY_READ_FAIL;

  sl_se_device_key_type_t kind;
  switch (cmd->msg.pubkey_get_cmd.kind) {
    case fwpb_pubkey_get_cmd_pubkey_type_BOOT_PUBKEY:
      kind = SL_SE_KEY_TYPE_IMMUTABLE_BOOT;
      break;
    case fwpb_pubkey_get_cmd_pubkey_type_AUTH_PUBKEY:
      kind = SL_SE_KEY_TYPE_IMMUTABLE_AUTH;
      break;
    case fwpb_pubkey_get_cmd_pubkey_type_ATTESTATION_PUBKEY:
      kind = SL_SE_KEY_TYPE_IMMUTABLE_ATTESTATION;
      break;
    case fwpb_pubkey_get_cmd_pubkey_type_SE_ATTESTATION_PUBKEY:
      kind = SL_SE_KEY_TYPE_IMMUTABLE_SE_ATTESTATION;
      break;
    default:
      goto out;
  }

  if (se_read_pubkey(kind, rsp->msg.pubkey_get_rsp.pubkey.bytes,
                     sizeof(rsp->msg.pubkey_get_rsp.pubkey.bytes)) != SL_STATUS_OK) {
    LOGE("Failed to get pubkey");
    rsp->msg.pubkey_get_rsp.rsp_status = fwpb_pubkey_get_rsp_pubkey_get_rsp_status_PUBKEY_READ_FAIL;
    goto out;
  }

  rsp->msg.pubkey_get_rsp.pubkey.size = SE_PUBKEY_SIZE;
  rsp->msg.pubkey_get_rsp.rsp_status = fwpb_pubkey_get_rsp_pubkey_get_rsp_status_SUCCESS;

out:
  proto_send_rsp(cmd, rsp);
}

void handle_fingerprint_settings_get(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_fingerprint_settings_get_rsp_tag;

  bool check_security_ok =
    bio_sensor_is_secured(&rsp->msg.fingerprint_settings_get_rsp.security_enabled);
  bool check_otp_ok = bio_sensor_is_otp_locked(&rsp->msg.fingerprint_settings_get_rsp.otp_locked);

  rsp->msg.fingerprint_settings_get_rsp.status =
    (check_security_ok && check_otp_ok)
      ? fwpb_fingerprint_settings_get_rsp_fingerprint_settings_get_rsp_status_SUCCESS
      : fwpb_fingerprint_settings_get_rsp_fingerprint_settings_get_rsp_status_FINGERPRINT_SETTINGS_READ_FAIL;

  proto_send_rsp(cmd, rsp);
}

void handle_cap_touch_calibrate(ipc_ref_t* message) {
  fwpb_wallet_cmd* wallet_cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* wallet_rsp = proto_get_rsp();

  wallet_rsp->which_msg = fwpb_wallet_rsp_cap_touch_cal_rsp_tag;
  fwpb_cap_touch_cal_rsp* rsp = &wallet_rsp->msg.cap_touch_cal_rsp;

  // Check if platform has cap touch calibration configured
  if (power_config.cap_touch_cal.hold_ms == 0) {
    rsp->status = fwpb_cap_touch_cal_rsp_cap_touch_cal_status_UNSUPPORTED;
    goto out;
  }

  static bool configured = false;
  if (!configured) {
    // Defer initialization of the GPIO because we've observed glitches
    // which erroneously trigger cap touch calibration on boot.
    mcu_gpio_configure(&power_config.cap_touch_cal.gpio, false);
  }

  rtos_timer_start(&sysinfo_task_priv.cap_touch_cal_timer, power_config.cap_touch_cal.hold_ms);
  if (sysinfo_task_priv.cap_touch_cal_timer.active) {
    LOGD("Starting cap touch calibration (%lims)", power_config.cap_touch_cal.hold_ms);
    mcu_gpio_set(&power_config.cap_touch_cal.gpio);
    rsp->status = fwpb_cap_touch_cal_rsp_cap_touch_cal_status_SUCCESS;
  } else {
    rsp->status = fwpb_cap_touch_cal_rsp_cap_touch_cal_status_ERROR;
  }

out:
  proto_send_rsp(wallet_cmd, wallet_rsp);
}

void handle_empty(ipc_ref_t* message) {
  fwpb_wallet_cmd* wallet_cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* wallet_rsp = proto_get_rsp();
  wallet_rsp->which_msg = fwpb_wallet_rsp_empty_rsp_tag;
  proto_send_rsp(wallet_cmd, wallet_rsp);
}

void handle_device_info(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  fwpb_device_info_rsp* info = &rsp->msg.device_info_rsp;
  rsp->which_msg = fwpb_wallet_rsp_device_info_rsp_tag;

  _Static_assert(SYSINFO_SERIAL_NUMBER_LENGTH == sizeof(info->serial) - 1, "wrong serial size");

  uint32_t length = 0;
  if (!sysinfo_assy_serial_read((char*)info->serial, &length)) {
    rsp->msg.device_info_rsp.rsp_status = fwpb_device_info_rsp_device_info_rsp_status_SERIAL_ERROR;
    goto out;
  }

  metadata_t metadata = {0};
  if (metadata_get_active_slot(&metadata, &rsp->msg.device_info_rsp.active_slot) !=
      METADATA_VALID) {
    rsp->msg.device_info_rsp.rsp_status =
      fwpb_device_info_rsp_device_info_rsp_status_METADATA_ERROR;
    goto out;
  }

  info->has_version = true;
  info->version.major = metadata.version.major;
  info->version.minor = metadata.version.minor;
  info->version.patch = metadata.version.patch;

  sysinfo_t* sysinfo = sysinfo_get();
  strncpy(info->sw_type, sysinfo->software_type, sizeof(info->sw_type));
  strncpy(info->hw_revision, sysinfo->hardware_revision, sizeof(info->hw_revision));

  if (!power_validate_fuel_gauge()) {
    rsp->msg.device_info_rsp.rsp_status = fwpb_device_info_rsp_device_info_rsp_status_BATTERY_ERROR;
  }

  power_get_battery(&rsp->msg.device_info_rsp.battery_charge, &rsp->msg.device_info_rsp.vcell,
                    &rsp->msg.device_info_rsp.avg_current_ma,
                    &rsp->msg.device_info_rsp.battery_cycles);

  se_get_secure_boot_config((secure_boot_config_t*)&rsp->msg.device_info_rsp.secure_boot_config);

  // Fingerprint matching stats
  rsp->msg.device_info_rsp.has_bio_match_stats = true;
  bio_match_stats_t* match_stats = bio_match_stats_get();
  rsp->msg.device_info_rsp.bio_match_stats.fail_count = match_stats->fail_count;

  // Intead of reading from the file system to determine the true template count, just use the max.
  // If a template isn't enrolled, it won't count towards a pass anyway.
  rsp->msg.device_info_rsp.bio_match_stats.pass_counts_count = TEMPLATE_MAX_COUNT;

  for (bio_template_id_t id = 0; id < TEMPLATE_MAX_COUNT; id++) {
    rsp->msg.device_info_rsp.bio_match_stats.pass_counts[id].pass_count =
      match_stats->pass_counts[id].tally;

    uint32_t version = 0;
    kv_result_t kv_result = bio_template_enrolled_by_version_get(id, &version);
    if (kv_result == KV_ERR_NONE) {
      uint32_t major = version >> 16;
      uint32_t minor = (version >> 8) & 0xFF;
      uint32_t patch = version & 0xFF;
      rsp->msg.device_info_rsp.bio_match_stats.pass_counts[id].firmware_version.major = major;
      rsp->msg.device_info_rsp.bio_match_stats.pass_counts[id].firmware_version.minor = minor;
      rsp->msg.device_info_rsp.bio_match_stats.pass_counts[id].firmware_version.patch = patch;
      rsp->msg.device_info_rsp.bio_match_stats.pass_counts[id].has_firmware_version = true;
    }
  }

  bio_match_stats_clear();  // Clear to avoid duplicate reporting

  rsp->msg.device_info_rsp.rsp_status = fwpb_device_info_rsp_device_info_rsp_status_SUCCESS;

out:
  proto_send_rsp(cmd, rsp);
}

static void cap_touch_cal_callback(rtos_timer_handle_t UNUSED(timer)) {
  LOGD("Cap touch calibration completed");
  mcu_gpio_clear(&power_config.cap_touch_cal.gpio);
  rtos_timer_stop(&sysinfo_task_priv.cap_touch_cal_timer);
}

NO_OPTIMIZE void handle_lock_device(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_lock_device_rsp_tag;
  SECURE_DO({ deauthenticate(); });
  proto_send_rsp(cmd, rsp);
}

void sysinfo_thread(void* UNUSED(args)) {
  rtos_timer_start(&sysinfo_task_priv.wdog_timer, sysinfo_task_priv.wdog_timer_refresh_ms);

  sleep_init((sleep_timer_callback_t)power_system_down_callback);
  // Device boots locked with power timer running.
  // Auth task waits for SYSEVENT_SLEEP_TIMER_READY before it can call sleep_stop_power_timer().
  sleep_start_power_timer();
  sysevent_set(SYSEVENT_SLEEP_TIMER_READY);

  sysevent_wait(SYSEVENT_FILESYSTEM_READY, true);

  if (!sysinfo_load()) {
    LOGW("Failed to load system information, using placeholders.");
  }

  feature_flags_init();

  if (kv_init((kv_api_t){.lock = &kv_mutex_lock, .unlock = &kv_mutex_unlock}) != KV_ERR_NONE) {
    LOGE("Failed to initialize key-value store");
    BITLOG_EVENT(kv_init, 0);
  }

  sysinfo_task_register_listeners();

  sysinfo_task_port_send_device_info();

  for (;;) {
    ipc_ref_t message = {0};
    ipc_recv(sysinfo_port, &message);

    if (sysinfo_task_port_handle_message(&message)) {
      continue;
    }

    switch (message.tag) {
      case IPC_SYSINFO_BOOT_STATUS:
        sysinfo_task_handle_coproc_boot(&message);
        break;
      case IPC_PROTO_META_CMD:
        handle_meta_cmd(&message);
        break;
      case IPC_PROTO_DEVICE_ID_CMD:
        handle_device_id_cmd(&message);
        break;
      case IPC_PROTO_WIPE_STATE_CMD:
        handle_wipe_state(&message);
        break;
      case IPC_PROTO_FUEL_CMD:
        handle_fuel_cmd(&message);
        break;
      case IPC_PROTO_COREDUMP_GET_CMD:
        handle_coredump_get(&message);
        break;
      case IPC_PROTO_EVENTS_GET_CMD:
        handle_events_get(&message);
        break;
      case IPC_PROTO_FEATURE_FLAGS_GET_CMD:
        handle_feature_flags_get(&message);
        break;
      case IPC_PROTO_FEATURE_FLAGS_SET_CMD:
        handle_feature_flags_set(&message);
        break;
      case IPC_PROTO_TELEMETRY_ID_GET_CMD:
        handle_telemetry_id_get(&message);
        break;
      case IPC_PROTO_SECINFO_GET_CMD:
        handle_secinfo_get(&message);
        break;
      case IPC_PROTO_CERT_GET_CMD:
        handle_cert_get(&message);
        break;
      case IPC_PROTO_PUBKEYS_GET_CMD:
        handle_pubkeys_get(&message);
        break;
      case IPC_PROTO_PUBKEY_GET_CMD:
        handle_pubkey_get(&message);
        break;
      case IPC_PROTO_FINGERPRINT_SETTINGS_GET_CMD:
        handle_fingerprint_settings_get(&message);
        break;
      case IPC_PROTO_CAP_TOUCH_CAL_CMD:
        handle_cap_touch_calibrate(&message);
        break;
      case IPC_PROTO_EMPTY_CMD:
        handle_empty(&message);
        break;
      case IPC_PROTO_DEVICE_INFO_CMD:
        handle_device_info(&message);
        break;
      case IPC_PROTO_LOCK_DEVICE_CMD:
        handle_lock_device(&message);
        break;
      case IPC_SYSINFO_COPROC_METADATA:
        sysinfo_task_handle_coproc_metadata(&message);
        break;
      case IPC_SYSINFO_POWER_OFF:
        LOGI("[Sysinfo] Power off requested");
        sysinfo_task_port_prepare_sleep_and_power_down();
        break;
      case IPC_SYSINFO_UXC_SLEEP_READY:
        LOGD("UXC sleep ready, powering down");
        power_set_ldo_low_power_mode();  // Reduce LDO quiescent current before sleep
        power_set_retain(false);
        break;
      default:
        LOGE("unknown message %ld", message.tag);
    }
  }
}

void sysinfo_task_create(const platform_hwrev_t hwrev) {
  sysinfo_task_priv.queue = rtos_queue_create(sysinfo_queue, ipc_ref_t, 4);
  ASSERT(sysinfo_task_priv.queue);
  ipc_register_port(sysinfo_port, sysinfo_task_priv.queue);

  sysinfo_task_priv.hwrev = hwrev;

  if (power_config.cap_touch_cal.hold_ms > 0) {
    rtos_timer_create_static(&sysinfo_task_priv.cap_touch_cal_timer, cap_touch_cal_callback);
  }

  rtos_thread_t* sysinfo_thread_handle =
    rtos_thread_create(sysinfo_thread, NULL, RTOS_THREAD_PRIORITY_NORMAL, 2048);
  ASSERT(sysinfo_thread_handle);

  rtos_mutex_create(&sysinfo_task_priv.kv_mutex);

  rtos_timer_create_static(&sysinfo_task_priv.wdog_timer, wdog_feed_callback);
}
