#include "attributes.h"
#include "ipc.h"
#include "log.h"
#include "metadata.h"
#include "power.h"
#include "proto_helpers.h"
#include "sysinfo_task_impl.h"
#include "wallet.pb.h"

#include <stdbool.h>
#include <stdint.h>

void sysinfo_task_port_send_device_info(void) {
  /* No-op on W1 - no display. */
}

void sysinfo_task_register_listeners(void) {
  /* No-op on W1. */
}

void sysinfo_task_handle_coproc_boot(ipc_ref_t* message) {
  /* No-op on W1. */
  (void)message;
}

bool sysinfo_task_port_handle_message(ipc_ref_t* message) {
  /* No platform-specific messages on W1. */
  (void)message;
  return false;
}

void sysinfo_task_handle_coproc_metadata(ipc_ref_t* message) {
  /* Should never be called. */
  (void)message;
  LOGE("unexpected call to coproc metadata");
}

void sysinfo_task_handle_coproc_coredump(ipc_ref_t* message) {
  /* Should never be called. */
  (void)message;
  LOGE("unexpected call to coproc coredump");
}

void sysinfo_task_handle_coproc_events(ipc_ref_t* message) {
  /* Should never be called. */
  (void)message;
  LOGE("unexpected call to coproc events");
}

void sysinfo_task_request_coproc_metadata(fwpb_wallet_cmd* cmd) {
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_meta_rsp_tag;
  rsp->msg.meta_rsp.rsp_status = fwpb_meta_rsp_meta_rsp_status_ERROR;
  rsp->msg.meta_rsp.mcu_role = cmd->msg.meta_cmd.mcu_role;
  proto_send_rsp(cmd, rsp);
}

void sysinfo_task_request_coproc_coredump(fwpb_wallet_cmd* cmd) {
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_coredump_get_rsp_tag;
  rsp->msg.coredump_get_rsp.rsp_status = fwpb_coredump_get_rsp_coredump_get_rsp_status_ERROR;
  rsp->msg.coredump_get_rsp.mcu_role = cmd->msg.coredump_get_cmd.mcu_role;
  proto_send_rsp(cmd, rsp);
}

void sysinfo_task_port_prepare_power_down(void) {
  ipc_send_empty(sysinfo_port, IPC_SYSINFO_POWER_OFF);
}

void sysinfo_task_port_power_down(void) {
  power_set_ldo_low_power_mode();
  power_set_retain(false);
}

void sysinfo_task_request_coproc_events(fwpb_wallet_cmd* cmd) {
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_events_get_rsp_tag;
  rsp->msg.events_get_rsp.rsp_status = fwpb_events_get_rsp_events_get_rsp_status_ERROR;
  rsp->msg.events_get_rsp.mcu_role = cmd->msg.events_get_cmd.mcu_role;
  proto_send_rsp(cmd, rsp);
}

bool sysinfo_task_port_dispatch_confirmation_result(ipc_ref_t* message) {
  /* Confirmation not supported on W1. */
  (void)message;
  return false;
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
}
