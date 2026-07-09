#include "sysinfo_task.h"

#include "assert.h"
#include "attributes.h"
#include "bitlog.h"
#include "log.h"
#include "mcu_devinfo.h"
#include "mcu_wdog.h"
#include "metadata.h"
#include "mpu_auto.h"
#include "rtos.h"
#include "rtos_timer.h"
#include "secure_channel_cert.h"
#include "sysevent.h"
#include "sysinfo.h"
#include "telemetry_storage.h"
#include "uc.h"
#include "uc_route.h"
#include "uxc.pb.h"
#include "wallet.pb.h"

#include <stdint.h>
#include <string.h>

#define SYSINFO_TASK_PRIORITY        (RTOS_THREAD_PRIORITY_NORMAL)
#define SYSINFO_TASK_STACK_SIZE      (4096u)
#define SYSINFO_TASK_QUEUE_SIZE      (4u)
#define SYSINFO_TASK_WDOG_REFRESH_MS (1000u)

static struct {
  /**
   * @brief Watchdog refresh timer.
   */
  rtos_timer_t wdog_timer;

  /**
   * @brief Watchdog refresh period (ms).
   */
  uint32_t wdog_timer_refresh_ms;
} sysinfo_task_priv SHARED_TASK_DATA = {
  .wdog_timer = {0},
  .wdog_timer_refresh_ms = SYSINFO_TASK_WDOG_REFRESH_MS,
};

static void _sysinfo_wdog_refresh_callback(rtos_timer_handle_t UNUSED(timer)) {
  mcu_wdog_feed();
  rtos_timer_restart(&sysinfo_task_priv.wdog_timer);
}

static void _sysinfo_task_send_empty_msg(void* proto) {
  // Proto is always empty besides tag.
  uc_free_recv_proto(proto);

  fwpb_uxc_msg_device* msg = uc_alloc_send_proto();
  ASSERT(msg != NULL);

  msg->which_msg = fwpb_uxc_msg_device_empty_rsp_tag;
  (void)uc_send(msg);
}

static void _sysinfo_task_copy_metadata_to_proto(metadata_t* metadata,
                                                 fwpb_firmware_metadata* proto) {
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
  memset(metadata, 0u, sizeof(*metadata));
}

static void _sysinfo_task_send_metadata(void* proto) {
  // Proto is always empty besides tag.
  uc_free_recv_proto(proto);

  fwpb_uxc_msg_device* msg = uc_alloc_send_proto();
  ASSERT(msg != NULL);

  msg->which_msg = fwpb_uxc_msg_device_meta_rsp_tag;
  fwpb_meta_rsp* rsp = &msg->msg.meta_rsp;
  memset(rsp, 0u, sizeof(*rsp));

  metadata_t metadata = {0};

  // Bootloader
  rsp->has_meta_bl = true;
  rsp->meta_bl.valid = metadata_get(META_TGT_BL, &metadata) == METADATA_VALID;
  _sysinfo_task_copy_metadata_to_proto(&metadata, &rsp->meta_bl);

  // Firmware Slot A
  rsp->meta_slot_a.valid = metadata_get(META_TGT_APP_A, &metadata) == METADATA_VALID;
  rsp->has_meta_slot_a = true;
  _sysinfo_task_copy_metadata_to_proto(&metadata, &rsp->meta_slot_a);

  // Firmware Slot B
  rsp->meta_slot_b.valid = metadata_get(META_TGT_APP_B, &metadata) == METADATA_VALID;
  rsp->has_meta_slot_b = true;
  _sysinfo_task_copy_metadata_to_proto(&metadata, &rsp->meta_slot_b);

  // Do not need to check the return code, as it is equivalent to:
  // `rsp->meta_slot_a.valid || rsp->meta_slot_b.valid`.
  (void)metadata_get_active_slot(&metadata, &rsp->active_slot);
  if (rsp->meta_bl.valid || rsp->meta_slot_a.valid || rsp->meta_slot_b.valid) {
    rsp->rsp_status = fwpb_meta_rsp_meta_rsp_status_SUCCESS;
  } else {
    rsp->rsp_status = fwpb_meta_rsp_meta_rsp_status_ERROR;
  }

  rsp->mcu_name = fwpb_mcu_name_MCU_NAME_STM32U5;
  rsp->mcu_role = fwpb_mcu_role_MCU_ROLE_UXC;

  (void)uc_send(msg);
}

static void _sysinfo_task_handle_coredump_command(void* proto) {
  fwpb_uxc_msg_host* cmd = (fwpb_uxc_msg_host*)proto;

  fwpb_uxc_msg_device* msg = uc_alloc_send_proto();
  ASSERT(msg != NULL);

  msg->which_msg = fwpb_uxc_msg_device_coredump_get_rsp_tag;
  fwpb_coredump_get_rsp* rsp = &msg->msg.coredump_get_rsp;

  switch (cmd->msg.coredump_get_cmd.type) {
    case fwpb_coredump_get_cmd_coredump_get_type_COUNT:
      rsp->coredump_count = telemetry_coredump_count();
      rsp->rsp_status = fwpb_coredump_get_rsp_coredump_get_rsp_status_SUCCESS;
      rsp->has_coredump_fragment = false;
      break;

    case fwpb_coredump_get_cmd_coredump_get_type_COREDUMP:
      if (telemetry_coredump_read_fragment(cmd->msg.coredump_get_cmd.offset,
                                           &rsp->coredump_fragment)) {
        rsp->rsp_status = fwpb_coredump_get_rsp_coredump_get_rsp_status_SUCCESS;
        rsp->has_coredump_fragment = true;
      } else {
        rsp->rsp_status = fwpb_coredump_get_rsp_coredump_get_rsp_status_ERROR;
      }
      break;

    case fwpb_coredump_get_cmd_coredump_get_type_UNSPECIFIED:
      /* 'break' intentionally omitted */

    default:
      rsp->rsp_status = fwpb_coredump_get_rsp_coredump_get_rsp_status_ERROR;
      break;
  }

  uc_free_recv_proto(proto);
  (void)uc_send(msg);
}

static void _sysinfo_task_send_events(void* proto) {
  // Proto is always empty besides tag.
  uc_free_recv_proto(proto);

  fwpb_uxc_msg_device* msg = uc_alloc_send_proto();
  ASSERT(msg != NULL);

  msg->which_msg = fwpb_uxc_msg_device_events_get_rsp_tag;

  uint32_t bytes_written = 0;
  fwpb_events_get_rsp* rsp = &msg->msg.events_get_rsp;
  rsp->rsp_status = fwpb_events_get_rsp_events_get_rsp_status_SUCCESS;
  rsp->version = EVENT_STORAGE_VERSION;
  rsp->has_fragment = true;
  rsp->fragment.remaining_size =
    bitlog_drain(rsp->fragment.data.bytes, sizeof(rsp->fragment.data.bytes), &bytes_written);
  rsp->fragment.data.size = bytes_written;

  (void)uc_send(msg);
}

static void _sysinfo_task_handle_cert_command(void* proto) {
  fwpb_uxc_msg_host* msg_host = (fwpb_uxc_msg_host*)proto;
  fwpb_cert_get_cmd* cmd = &msg_host->msg.cert_get_cmd;

  fwpb_cert_get_cmd cmd_local = *cmd;
  uc_free_recv_proto(proto);

  fwpb_uxc_msg_device* msg = uc_alloc_send_proto();
  ASSERT(msg != NULL);

  msg->which_msg = fwpb_uxc_msg_device_cert_get_rsp_tag;
  fwpb_cert_get_rsp* rsp = &msg->msg.cert_get_rsp;

  secure_channel_cert_handle_cmd_get(&cmd_local, rsp);

  (void)uc_send(msg);
}

static void _sysinfo_send_cert_request(void) {
  secure_channel_cert_data_t cert_data = {0};
  if (secure_channel_read_cert(SC_CERT_CORE_ID, &cert_data)) {
    LOGI("Using pinned %s certificate.", SC_CERT_CORE_ID);
  } else {
    LOGW("Pinned %s cert not found, requesting", SC_CERT_CORE_ID);
    fwpb_uxc_msg_device* msg = uc_alloc_send_proto();
    ASSERT(msg != NULL);

    msg->which_msg = fwpb_uxc_msg_device_cert_get_cmd_tag;
    msg->msg.cert_get_cmd.kind = fwpb_cert_get_cmd_cert_type_DEVICE_SECURE_CHANNEL_CERT;
    strncpy(msg->msg.cert_get_cmd.cert_id, SC_CERT_CORE_ID, sizeof(msg->msg.cert_get_cmd.cert_id));
    LOGI("Sending cert get cmd");
    (void)uc_send(msg);
  }
}

static void _sysinfo_task_handle_cert_response(void* proto) {
  fwpb_uxc_msg_host* msg_host = (fwpb_uxc_msg_host*)proto;
  ASSERT(msg_host->which_msg == fwpb_uxc_msg_host_cert_get_rsp_tag);
  fwpb_cert_get_rsp* get_cert_rsp = &msg_host->msg.cert_get_rsp;

  /* Ensure certificate retrieval was successful before using certificate data. */
  if (get_cert_rsp->rsp_status != fwpb_cert_get_rsp_cert_get_rsp_status_SUCCESS) {
    LOGE("Cert get response error, status: %d", get_cert_rsp->rsp_status);
    uc_free_recv_proto(msg_host);
    return;
  }
  // Copy cert data to stack and free recv buffer immediately to avoid
  // holding a shared UC recv buffer during flash write.
  secure_channel_cert_data_t cert_local = *(secure_channel_cert_data_t*)&get_cert_rsp->cert.bytes;
  uc_free_recv_proto(msg_host);

  if (cert_local.type != CERT_TYPE_PICOCERT) {
    LOGE("Invalid Certificate Type");
    return;
  }
  if (strncmp(cert_local.data.picocert.subject, SC_CERT_CORE_ID,
              sizeof(cert_local.data.picocert.subject)) != 0) {
    LOGE("Invalid Certificate Subject");
    return;
  }

  secure_channel_cert_err_t err = secure_channel_pin_cert(&cert_local);
  if (err != SECURE_CHANNEL_CERT_OK) {
    LOGE("Error while pinning cert: %d", err);
    return;
  } else {
    LOGI("Pinned certificate: %s", cert_local.data.picocert.subject);
  }
}

static void _sysinfo_task_send_boot_msg(void) {
  fwpb_uxc_msg_device* msg = uc_alloc_send_proto();
  ASSERT(msg != NULL);

  msg->which_msg = fwpb_uxc_msg_device_boot_status_msg_tag;
  fwpb_uxc_boot_status_msg* rsp = &msg->msg.boot_status_msg;
  rsp->mcu_id = fwpb_uxc_boot_status_msg_uxc_mcu_id_UXC;
  {
    uint8_t chip_id[CHIPID_LENGTH] = {0};
    mcu_devinfo_chipid(chip_id);
    rsp->chip_id.size = CHIPID_LENGTH;
    memcpy(rsp->chip_id.bytes, chip_id, CHIPID_LENGTH);
  }
  secure_channel_cert_data_t cert_data = {0};
  if (secure_channel_read_cert(SC_CERT_CORE_ID, &cert_data)) {
    rsp->auth_status = fwpb_uxc_auth_status_UXC_AUTH_STATUS_UNLOCKED;
  } else {
    rsp->auth_status = fwpb_uxc_auth_status_UXC_AUTH_STATUS_UNAUTHENTICATED;
  }
  metadata_t metadata = {0};
  fwpb_firmware_slot active_slot = fwpb_firmware_slot_SLOT_A;
  if (metadata_get_active_slot(&metadata, &active_slot) == METADATA_VALID) {
    rsp->has_version = true;
    rsp->version.major = metadata.version.major;
    rsp->version.minor = metadata.version.minor;
    rsp->version.patch = metadata.version.patch;
    rsp->active_slot = active_slot;
  }

  (void)uc_send(msg);
}

static void sysinfo_thread(void* args) {
  rtos_queue_t* queue = args;
  ASSERT(queue != NULL);

  // Start the watchdog timer; watchdog is pet on a timer thread.
  rtos_timer_start(&sysinfo_task_priv.wdog_timer, sysinfo_task_priv.wdog_timer_refresh_ms);

  sysevent_wait(SYSEVENT_FILESYSTEM_READY, true);
  secure_channel_cert_init();

  uc_route_register_queue(fwpb_uxc_msg_host_empty_cmd_tag, queue);
  uc_route_register_queue(fwpb_uxc_msg_host_meta_cmd_tag, queue);
  uc_route_register_queue(fwpb_uxc_msg_host_coredump_get_cmd_tag, queue);
  uc_route_register_queue(fwpb_uxc_msg_host_events_get_cmd_tag, queue);
  uc_route_register_queue(fwpb_uxc_msg_host_cert_get_cmd_tag, queue);
  uc_route_register_queue(fwpb_uxc_msg_host_cert_get_rsp_tag, queue);
  uc_route_register_queue(fwpb_uxc_msg_host_boot_status_msg_tag, queue);

  _sysinfo_task_send_boot_msg();

  while (true) {
    fwpb_uxc_msg_host* proto = uc_route_pend_queue(queue);
    ASSERT(proto != NULL);

    switch (proto->which_msg) {
      case fwpb_uxc_msg_host_boot_status_msg_tag:
        // Core piggybacks the device serial here so our Memfault events
        // report the same identity. Empty/short payloads are ignored by
        // sysinfo_set_serial so old Core builds remain compatible.
        sysinfo_set_serial((const char*)proto->msg.boot_status_msg.serial.bytes,
                           proto->msg.boot_status_msg.serial.size);
        uc_free_recv_proto(proto);
        _sysinfo_send_cert_request();
        break;

      case fwpb_uxc_msg_host_empty_cmd_tag:
        _sysinfo_task_send_empty_msg(proto);
        break;

      case fwpb_uxc_msg_host_meta_cmd_tag:
        _sysinfo_task_send_metadata(proto);
        break;

      case fwpb_uxc_msg_host_coredump_get_cmd_tag:
        _sysinfo_task_handle_coredump_command(proto);
        break;

      case fwpb_uxc_msg_host_events_get_cmd_tag:
        _sysinfo_task_send_events(proto);
        break;

      case fwpb_uxc_msg_host_cert_get_cmd_tag:
        _sysinfo_task_handle_cert_command(proto);
        break;

      case fwpb_uxc_msg_host_cert_get_rsp_tag:
        _sysinfo_task_handle_cert_response(proto);
        break;

      default:
        uc_free_recv_proto(proto);
        break;
    }
  }
}

void sysinfo_task_create(const platform_hwrev_t UNUSED(hwrev)) {
  rtos_timer_create_static(&sysinfo_task_priv.wdog_timer, _sysinfo_wdog_refresh_callback);

  rtos_queue_t* queue =
    rtos_queue_create(sysinfo_task_queue, fwpb_uxc_msg_host*, SYSINFO_TASK_QUEUE_SIZE);
  rtos_thread_t* thread =
    rtos_thread_create(sysinfo_thread, queue, SYSINFO_TASK_PRIORITY, SYSINFO_TASK_STACK_SIZE);
  ASSERT(thread != NULL);
}
