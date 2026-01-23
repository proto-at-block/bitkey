#include "assert.h"
#include "attributes.h"
#include "bitlog.h"
#include "ipc.h"
#include "kv.h"
#include "log.h"
#include "metadata.h"
#include "power.h"
#include "proto_helpers.h"
#include "rtos.h"
#include "sysinfo.h"
#include "sysinfo_task_impl.h"
#include "uc.h"
#include "uc_route.h"
#include "ui_messaging.h"
#include "uxc.pb.h"

#include <string.h>

#define SLEEP_PREP_TIMEOUT_MS (500)
#define BITLOG_SAVE_DELAY_MS  (50)

static SHARED_TASK_DATA device_info_t device_info_for_ui;

static void _sysinfo_task_handle_coproc_boot_message(void* proto, void* UNUSED(context));
static void _sysinfo_task_handle_coproc_metadata(void* proto, void* UNUSED(context));

static void send_initial_device_info(void) {
  memset(&device_info_for_ui, 0, sizeof(device_info_for_ui));

  // Load device info from metadata
  metadata_t metadata = {0};
  fwpb_firmware_slot active_slot = fwpb_firmware_slot_SLOT_A;
  if (metadata_get_active_slot(&metadata, &active_slot) == METADATA_VALID) {
    snprintf(device_info_for_ui.firmware_version, sizeof(device_info_for_ui.firmware_version),
             "v%u.%u.%u", metadata.version.major, metadata.version.minor, metadata.version.patch);
    strncpy(device_info_for_ui.hardware_version, metadata.hardware_revision,
            sizeof(device_info_for_ui.hardware_version) - 1);
  } else {
    strncpy(device_info_for_ui.firmware_version, "Unknown",
            sizeof(device_info_for_ui.firmware_version) - 1);
    strncpy(device_info_for_ui.hardware_version, "Unknown",
            sizeof(device_info_for_ui.hardware_version) - 1);
  }

  // Get device serial number
  uint32_t serial_length = sizeof(device_info_for_ui.serial_number) - 1;
  if (!sysinfo_assy_serial_read(device_info_for_ui.serial_number, &serial_length) ||
      serial_length == 0) {
    strncpy(device_info_for_ui.serial_number, "Unknown",
            sizeof(device_info_for_ui.serial_number) - 1);
  }

  // Load brightness from KV
  device_info_for_ui.brightness_percent = 80;  // Default
  uint8_t brightness_len = sizeof(device_info_for_ui.brightness_percent);
  kv_result_t result = kv_get("disp_bri", &device_info_for_ui.brightness_percent, &brightness_len);
  if (result != KV_ERR_NONE && result != KV_ERR_NOT_FOUND) {
    LOGE("Failed to load brightness from KV (err=%d), using default", result);
    device_info_for_ui.brightness_percent = 80;  // Explicitly reset to default
  }

  // Send device info with brightness to UI task
  UI_SHOW_EVENT_WITH_DATA(UI_EVENT_SET_DEVICE_INFO, &device_info_for_ui,
                          sizeof(device_info_for_ui));
}

NO_OPTIMIZE static void handle_set_brightness_internal(ipc_ref_t* message) {
  sysinfo_set_brightness_internal_t* req = (sysinfo_set_brightness_internal_t*)message->object;

  kv_result_t result =
    kv_set("disp_bri", &req->brightness_percent, sizeof(req->brightness_percent));
  if (result != KV_ERR_NONE) {
    LOGE("Failed to save brightness to KV (err=%d)", result);
  }
}

void sysinfo_task_port_send_device_info(void) {
  send_initial_device_info();
}

void sysinfo_task_register_listeners(void) {
  uc_route_register(fwpb_uxc_msg_device_boot_status_msg_tag,
                    _sysinfo_task_handle_coproc_boot_message, NULL);
  uc_route_register(fwpb_uxc_msg_device_meta_rsp_tag, _sysinfo_task_handle_coproc_metadata, NULL);
}

bool sysinfo_task_port_handle_message(ipc_ref_t* message) {
  switch (message->tag) {
    case IPC_SYSINFO_SET_BRIGHTNESS_INTERNAL:
      handle_set_brightness_internal(message);
      return true;
    default:
      return false;
  }
}

void sysinfo_task_handle_coproc_boot(ipc_ref_t* message) {
  (void)message;

  fwpb_uxc_msg_host* msg = uc_alloc_send_proto();
  ASSERT(msg != NULL);

  msg->which_msg = fwpb_uxc_msg_host_boot_status_msg_tag;
  fwpb_uxc_boot_status_msg* rsp = &msg->msg.boot_status_msg;
  rsp->mcu_id = fwpb_uxc_boot_status_msg_uxc_mcu_id_CORE;
  rsp->auth_status = fwpb_uxc_auth_status_UXC_AUTH_STATUS_UNAUTHENTICATED;

  (void)uc_send(msg);
}

void sysinfo_task_handle_coproc_metadata(ipc_ref_t* message) {
  ASSERT((message != NULL) && (message->object != NULL));

  fwpb_uxc_msg_device* msg_device = message->object;
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_meta_rsp_tag;
  memcpy(&rsp->msg.meta_rsp, &msg_device->msg.meta_rsp, sizeof(msg_device->msg.meta_rsp));
  uc_free_recv_proto(msg_device);

  // Send the IPC message.
  proto_send_rsp_without_free(rsp);
  ipc_proto_free((uint8_t*)rsp);
}

void sysinfo_task_request_coproc_metadata(fwpb_wallet_cmd* cmd) {
  fwpb_uxc_msg_host* msg = uc_alloc_send_proto();

  // Copy over the message.
  msg->which_msg = fwpb_uxc_msg_host_meta_cmd_tag;
  ipc_proto_free((uint8_t*)cmd);

  const bool sent = uc_send(msg);
  if (!sent) {
    // Force a failure response.
    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->which_msg = fwpb_wallet_rsp_meta_rsp_tag;
    rsp->msg.meta_rsp.rsp_status = fwpb_meta_rsp_meta_rsp_status_ERROR;
    proto_send_rsp_without_free(rsp);
    ipc_proto_free((uint8_t*)rsp);
  }
}

static void _sysinfo_task_handle_coproc_boot_message(void* proto, void* UNUSED(context)) {
  static sysinfo_boot_status_t sysinfo_boot_status SHARED_TASK_DATA;

  uc_free_recv_proto(proto);

  ipc_send(sysinfo_port, &sysinfo_boot_status, sizeof(sysinfo_boot_status),
           IPC_SYSINFO_BOOT_STATUS);
  // Tell key_manager that the UXC is active so it can handle key agreement
  ipc_send(key_manager_port, NULL, 0, IPC_KEY_MANAGER_UXC_BOOT);
}

static void _sysinfo_task_handle_coproc_metadata(void* proto, void* UNUSED(context)) {
  // Pass through the pointer. Sysinfo task will handle free'ing the data.
  ipc_send(sysinfo_port, proto, sizeof(proto), IPC_SYSINFO_COPROC_METADATA);
}

void sysinfo_task_port_prepare_sleep_and_power_down(void) {
  LOGD("Preparing for coordinated power down");

  // Send prepare sleep command to UXC
  fwpb_uxc_msg_host* cmd = uc_alloc_send_proto();
  if (cmd) {
    cmd->which_msg = fwpb_uxc_msg_host_display_cmd_tag;
    cmd->msg.display_cmd.which_command = fwpb_display_command_prepare_sleep_tag;

    if (!uc_send(cmd)) {
      LOGE("Failed to send prepare sleep command to UXC");
    }
  } else {
    LOGE("Failed to allocate proto for prepare sleep command");
  }

  // Wait for UXC to enter monitor mode and send IPC_SYSINFO_UXC_SLEEP_READY
  // (If IPC arrives first, it will power down immediately)
  rtos_thread_sleep(SLEEP_PREP_TIMEOUT_MS);
  // Timeout - UXC didn't respond in time
  BITLOG_EVENT(sleep_prep_timeout, 0);
  // Give bitlog time to save
  rtos_thread_sleep(BITLOG_SAVE_DELAY_MS);

  LOGW("UXC sleep ready timeout - powering down anyway");
  power_set_ldo_low_power_mode();  // Reduce LDO quiescent current before sleep
  power_set_retain(false);
}
