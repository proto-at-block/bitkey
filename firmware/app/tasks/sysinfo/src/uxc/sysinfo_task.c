#include "sysinfo_task.h"

#include "assert.h"
#include "attributes.h"
#include "bitlog.h"
#include "mcu_wdog.h"
#include "metadata.h"
#include "mpu_auto.h"
#include "rtos.h"
#include "rtos_timer.h"
#include "telemetry_storage.h"
#include "uc.h"
#include "uc_route.h"
#include "uxc.pb.h"
#include "wallet.pb.h"

#include <stdint.h>
#include <string.h>

#define SYSINFO_TASK_PRIORITY        (RTOS_THREAD_PRIORITY_NORMAL)
#define SYSINFO_TASK_STACK_SIZE      (2048u)
#define SYSINFO_TASK_QUEUE_SIZE      (2u)
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
  fwpb_uxc_msg_device* msg = uc_alloc_send_proto();
  ASSERT(msg != NULL);

  msg->which_msg = fwpb_uxc_msg_device_cert_get_rsp_tag;

  // TODO(W-14108): Add support for certificate exchange for Secure Comms.
  fwpb_cert_get_cmd* cmd = &((fwpb_uxc_msg_host*)proto)->msg.cert_get_cmd;
  fwpb_cert_get_rsp* rsp = &msg->msg.cert_get_rsp;
  switch (cmd->kind) {
    case fwpb_cert_get_cmd_cert_type_BATCH_CERT:
      /* 'break' intentionally omitted */

    case fwpb_cert_get_cmd_cert_type_DEVICE_SE_CERT:
      /* 'break' intentionally omitted */

    case fwpb_cert_get_cmd_cert_type_DEVICE_HOST_CERT:
      rsp->rsp_status = fwpb_cert_get_rsp_cert_get_rsp_status_UNIMPLEMENTED;
      rsp->cert.size = 0;
      break;

    case fwpb_cert_get_cmd_cert_type_UNSPECIFIED:
      /* 'break' intentionally omitted */

    default:
      rsp->rsp_status = fwpb_cert_get_rsp_cert_get_rsp_status_UNSPECIFIED;
      break;
  }

  uc_free_recv_proto(proto);
  (void)uc_send(msg);
}

static void _sysinfo_task_handle_cert_response(void* proto) {
  // TODO(W-14108): Add support for certificate exchange for Secure Comms.
  uc_free_recv_proto(proto);
}

static void _sysinfo_task_send_boot_msg(void) {
  fwpb_uxc_msg_device* msg = uc_alloc_send_proto();
  ASSERT(msg != NULL);

  msg->which_msg = fwpb_uxc_msg_device_boot_status_msg_tag;
  fwpb_uxc_boot_status_msg* rsp = &msg->msg.boot_status_msg;
  rsp->mcu_id = fwpb_uxc_boot_status_msg_uxc_mcu_id_UXC;
  rsp->auth_status = fwpb_uxc_auth_status_UXC_AUTH_STATUS_UNAUTHENTICATED;

  metadata_t metadata = {0};
  if ((metadata_get(META_TGT_APP_A, &metadata) == METADATA_VALID) ||
      (metadata_get(META_TGT_APP_B, &metadata) == METADATA_VALID)) {
    rsp->has_version = true;
    rsp->version.major = metadata.version.major;
    rsp->version.minor = metadata.version.minor;
    rsp->version.patch = metadata.version.patch;
  }

  (void)uc_send(msg);
}

static void sysinfo_thread(void* args) {
  rtos_queue_t* queue = args;
  ASSERT(queue != NULL);

  // Start the watchdog timer; watchdog is pet on a timer thread.
  rtos_timer_start(&sysinfo_task_priv.wdog_timer, sysinfo_task_priv.wdog_timer_refresh_ms);

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
        uc_free_recv_proto(proto);
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
