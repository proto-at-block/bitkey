#include "arithmetic.h"
#include "assert.h"
#include "attributes.h"
#include "bitlog.h"
#include "confirmation_manager.h"
#include "coproc_power.h"
#include "exti.h"
#include "ipc.h"
#include "kv.h"
#include "log.h"
#include "mcu_reset.h"
#include "metadata.h"
#include "power.h"
#include "proto_helpers.h"
#include "rtos.h"
#include "sysevent.h"
#include "sysinfo.h"
#include "sysinfo_task_impl.h"
#include "uc.h"
#include "uc_route.h"
#include "ui_messaging.h"
#include "uxc.pb.h"

#include <string.h>

#define SYSINFO_SLEEP_PREP_TIMEOUT_MS (500)

/**
 * @brief Delay, after power off, to check for a touch event if USB is plugged
 * in, preventing power off.
 */
#define SYSINFO_POWER_OFF_TOUCH_DELAY_MS (50)

/**
 * @brief Polling frequency to check for a touch or USB un-plug event during
 * device power off.
 */
#define SYSINFO_POWER_OFF_POLL_MS (10)

extern power_config_t power_config;

static SHARED_TASK_DATA device_info_t device_info_for_ui;
static SHARED_TASK_DATA fwpb_device_info_rsp_device_info_mcu uxc_mcu_info = {0};

static void _sysinfo_task_handle_coproc_boot_message(void* proto, void* UNUSED(context));
static void _sysinfo_task_handle_coproc_metadata(void* proto, void* UNUSED(context));
static void _sysinfo_task_handle_coproc_coredump(void* proto, void* UNUSED(context));
static void _sysinfo_task_handle_coproc_events(void* proto, void* UNUSED(context));

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
  uc_route_register(fwpb_uxc_msg_device_coredump_get_rsp_tag, _sysinfo_task_handle_coproc_coredump,
                    NULL);
  uc_route_register(fwpb_uxc_msg_device_events_get_rsp_tag, _sysinfo_task_handle_coproc_events,
                    NULL);
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
  proto_send_rsp(NULL, rsp);
}

void sysinfo_task_handle_coproc_coredump(ipc_ref_t* message) {
  ASSERT((message != NULL) && (message->object != NULL));

  fwpb_uxc_msg_device* msg_device = message->object;
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_coredump_get_rsp_tag;
  rsp->msg.coredump_get_rsp.rsp_status = msg_device->msg.coredump_get_rsp.rsp_status;
  rsp->msg.coredump_get_rsp.coredump_count = msg_device->msg.coredump_get_rsp.coredump_count;
  rsp->msg.coredump_get_rsp.mcu_role = msg_device->msg.coredump_get_rsp.mcu_role;
  rsp->msg.coredump_get_rsp.mcu_name = msg_device->msg.coredump_get_rsp.mcu_name;
  rsp->msg.coredump_get_rsp.has_coredump_fragment =
    msg_device->msg.coredump_get_rsp.has_coredump_fragment;

  // If there is a coredump fragment present, then copy it over.
  if (rsp->msg.coredump_get_rsp.has_coredump_fragment) {
    rsp->msg.coredump_get_rsp.coredump_fragment.offset =
      msg_device->msg.coredump_get_rsp.coredump_fragment.offset;
    rsp->msg.coredump_get_rsp.coredump_fragment.complete =
      msg_device->msg.coredump_get_rsp.coredump_fragment.complete;
    rsp->msg.coredump_get_rsp.coredump_fragment.coredumps_remaining =
      msg_device->msg.coredump_get_rsp.coredump_fragment.coredumps_remaining;

    const size_t coredump_size =
      BLK_MIN(msg_device->msg.coredump_get_rsp.coredump_fragment.data.size,
              sizeof(rsp->msg.coredump_get_rsp.coredump_fragment.data.bytes));
    memcpy(rsp->msg.coredump_get_rsp.coredump_fragment.data.bytes,
           msg_device->msg.coredump_get_rsp.coredump_fragment.data.bytes, coredump_size);
    msg_device->msg.coredump_get_rsp.coredump_fragment.data.size = coredump_size;
  }
  uc_free_recv_proto(msg_device);

  // Send the IPC message.
  proto_send_rsp(NULL, rsp);
}

void sysinfo_task_handle_coproc_events(ipc_ref_t* message) {
  ASSERT((message != NULL) && (message->object != NULL));

  fwpb_uxc_msg_device* msg_device = message->object;
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_events_get_rsp_tag;
  rsp->msg.events_get_rsp.rsp_status = msg_device->msg.events_get_rsp.rsp_status;
  rsp->msg.events_get_rsp.version = msg_device->msg.events_get_rsp.version;
  rsp->msg.events_get_rsp.mcu_role = msg_device->msg.events_get_rsp.mcu_role;
  rsp->msg.events_get_rsp.has_fragment = msg_device->msg.events_get_rsp.has_fragment;

  // If there is a fragment present, then copy it over.
  if (rsp->msg.events_get_rsp.has_fragment) {
    rsp->msg.events_get_rsp.fragment.remaining_size =
      msg_device->msg.events_get_rsp.fragment.remaining_size;

    const size_t fragment_size = BLK_MIN(msg_device->msg.events_get_rsp.fragment.data.size,
                                         sizeof(rsp->msg.events_get_rsp.fragment.data.bytes));
    memcpy(rsp->msg.events_get_rsp.fragment.data.bytes,
           msg_device->msg.events_get_rsp.fragment.data.bytes, fragment_size);
    rsp->msg.events_get_rsp.fragment.data.size = fragment_size;
  }
  uc_free_recv_proto(msg_device);

  // Send the IPC message.
  proto_send_rsp(NULL, rsp);
}

void sysinfo_task_request_coproc_metadata(fwpb_wallet_cmd* cmd) {
  fwpb_uxc_msg_host* msg = uc_alloc_send_proto();
  const uint8_t mcu_role = cmd->msg.meta_cmd.mcu_role;

  // Copy over the message.
  msg->which_msg = fwpb_uxc_msg_host_meta_cmd_tag;
  msg->msg.meta_cmd.mcu_role = mcu_role;
  ipc_proto_free((uint8_t*)cmd);

  const bool sent = uc_send(msg);
  if (!sent) {
    // Force a failure response.
    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->which_msg = fwpb_wallet_rsp_meta_rsp_tag;
    rsp->msg.meta_rsp.rsp_status = fwpb_meta_rsp_meta_rsp_status_ERROR;
    rsp->msg.meta_rsp.mcu_role = mcu_role;
    proto_send_rsp(NULL, rsp);
  }
}

void sysinfo_task_request_coproc_coredump(fwpb_wallet_cmd* cmd) {
  fwpb_uxc_msg_host* msg = uc_alloc_send_proto();
  const uint8_t mcu_role = cmd->msg.coredump_get_cmd.mcu_role;

  // Copy over the message.
  msg->which_msg = fwpb_uxc_msg_host_coredump_get_cmd_tag;
  msg->msg.coredump_get_cmd.type = cmd->msg.coredump_get_cmd.type;
  msg->msg.coredump_get_cmd.offset = cmd->msg.coredump_get_cmd.offset;
  msg->msg.coredump_get_cmd.mcu_role = mcu_role;
  ipc_proto_free((uint8_t*)cmd);

  const bool sent = uc_send(msg);
  if (!sent) {
    // Force a failure response.
    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->which_msg = fwpb_wallet_rsp_coredump_get_rsp_tag;
    rsp->msg.coredump_get_rsp.rsp_status = fwpb_coredump_get_rsp_coredump_get_rsp_status_ERROR;
    rsp->msg.coredump_get_rsp.mcu_role = mcu_role;
    proto_send_rsp(NULL, rsp);
  }
}

void sysinfo_task_request_coproc_events(fwpb_wallet_cmd* cmd) {
  fwpb_uxc_msg_host* msg = uc_alloc_send_proto();
  const uint8_t mcu_role = cmd->msg.events_get_cmd.mcu_role;

  // Copy over the message.
  msg->which_msg = fwpb_uxc_msg_host_events_get_cmd_tag;
  msg->msg.events_get_cmd.mcu_role = mcu_role;
  ipc_proto_free((uint8_t*)cmd);

  const bool sent = uc_send(msg);
  if (!sent) {
    // Force a failure response.
    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->which_msg = fwpb_wallet_rsp_events_get_rsp_tag;
    rsp->msg.events_get_rsp.rsp_status = fwpb_events_get_rsp_events_get_rsp_status_ERROR;
    rsp->msg.events_get_rsp.mcu_role = mcu_role;
    proto_send_rsp(NULL, rsp);
  }
}

static void _sysinfo_task_handle_coproc_boot_message(void* proto, void* UNUSED(context)) {
  static sysinfo_boot_status_t sysinfo_boot_status SHARED_TASK_DATA;

  fwpb_uxc_boot_status_msg* msg = &((fwpb_uxc_msg_device*)proto)->msg.boot_status_msg;
  uxc_mcu_info.mcu_role = fwpb_mcu_role_MCU_ROLE_UXC;
  uxc_mcu_info.mcu_name = fwpb_mcu_name_MCU_NAME_STM32U5;
  uxc_mcu_info.version.major = msg->version.major;
  uxc_mcu_info.version.minor = msg->version.minor;
  uxc_mcu_info.version.patch = msg->version.patch;
  uxc_mcu_info.has_version = true;

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

void sysinfo_task_port_prepare_power_down(void) {
  LOGD("Preparing for coordinated power down");
  UI_SHOW_EVENT(UI_EVENT_POWER_OFF);

  // If USB is plugged in, we cannot power off, so instead we turn off the
  // screen and poll until USB is un-plugged.
  if (power_is_plugged_in()) {
    // We clear before polling to ensure the touch event came in after we
    // started polling.
    rtos_thread_sleep(SYSINFO_POWER_OFF_TOUCH_DELAY_MS);
    sysevent_clear(SYSEVENT_TOUCH | SYSEVENT_CAPTOUCH);

    // If there is a touch event (captouch or screen) while USB is plugged
    // in, we exit to reset under the assumption that the user wants to
    // use their device.
    while (power_is_plugged_in() && !sysevent_get(SYSEVENT_TOUCH | SYSEVENT_CAPTOUCH)) {
      rtos_thread_sleep(SYSINFO_POWER_OFF_POLL_MS);
    }
  }

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

  // If we successfully send the message to the UXC, we expect to get a
  // response to continue the shutdown flow, so start a shutdown timer to
  // handle the case in which we do not get the response.
  sysinfo_task_start_shutdown_timer(SYSINFO_SLEEP_PREP_TIMEOUT_MS);
}

void sysinfo_task_port_power_down(void) {
  if (power_is_plugged_in()) {
    // If USB is plugged in, we cannot power down, so instead we just reset.
    coproc_power_assert_reset();
    mcu_reset_with_reason(MCU_RESET_POWER_DOWN_USB_PLUGGED);
  } else {
    if (sysinfo_task_in_ship_state()) {
      // Disable LDO completely for ship state (packout mode).
      power_disable_ldo();
    } else {
      // Reduce LDO quiescent current before sleep.
      power_set_ldo_low_power_mode();
    }
    power_set_retain(false);
  }
}

void sysinfo_task_port_populate_mcu_info(fwpb_device_info_rsp* rsp) {
  ASSERT(rsp != NULL);

  uint8_t index = 0;
  metadata_t metadata = {0};
  fwpb_firmware_slot active_slot = fwpb_firmware_slot_SLOT_A;
  if (metadata_get_active_slot(&metadata, &active_slot) != METADATA_VALID) {
    rsp->device_info_mcus_count = 0;
    return;
  }

  rsp->device_info_mcus_count = index + 1;
  rsp->device_info_mcus[index].mcu_role = fwpb_mcu_role_MCU_ROLE_CORE;
  rsp->device_info_mcus[index].mcu_name = fwpb_mcu_name_MCU_NAME_EFR32;
  rsp->device_info_mcus[index].has_version = true;
  rsp->device_info_mcus[index].version.major = metadata.version.major;
  rsp->device_info_mcus[index].version.minor = metadata.version.minor;
  rsp->device_info_mcus[index].version.patch = metadata.version.patch;
  index++;

  if (uxc_mcu_info.has_version) {
    rsp->device_info_mcus_count = index + 1;
    rsp->device_info_mcus[index].mcu_role = uxc_mcu_info.mcu_role;
    rsp->device_info_mcus[index].mcu_name = uxc_mcu_info.mcu_name;
    rsp->device_info_mcus[index].has_version = true;
    rsp->device_info_mcus[index].version.major = uxc_mcu_info.version.major;
    rsp->device_info_mcus[index].version.minor = uxc_mcu_info.version.minor;
    rsp->device_info_mcus[index].version.patch = uxc_mcu_info.version.patch;
    index++;
  }
}

static void _sysinfo_task_handle_coproc_coredump(void* proto, void* UNUSED(context)) {
  // Pass through the pointer. Sysinfo task will handle free'ing the data.
  ipc_send(sysinfo_port, proto, sizeof(proto), IPC_SYSINFO_COPROC_COREDUMP);
}

static void _sysinfo_task_handle_coproc_events(void* proto, void* UNUSED(context)) {
  // Pass through the pointer. Sysinfo task will handle free'ing the data.
  ipc_send(sysinfo_port, proto, sizeof(proto), IPC_SYSINFO_COPROC_EVENTS);
}

bool sysinfo_task_port_dispatch_confirmation_result(ipc_ref_t* message) {
  return confirmation_manager_dispatch_result(message);
}
