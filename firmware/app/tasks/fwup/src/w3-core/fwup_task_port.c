#include "assert.h"
#include "auth.h"
#include "fwup.h"
#include "fwup_task_impl.h"
#include "ipc.h"
#include "proto_helpers.h"
#include "secutils.h"
#include "uc.h"
#include "uc_route.h"
#include "ui_messaging.h"
#include "wallet.pb.h"

#include <stdbool.h>
#include <stdint.h>
#include <string.h>

static void _handle_fwup_start(void* proto, void* UNUSED(context));
static void _handle_fwup_transfer(void* proto, void* UNUSED(context));
static void _handle_fwup_finish(void* proto, void* UNUSED(context));

void fwup_task_register_listeners(void) {
  uc_route_register(fwpb_uxc_msg_device_fwup_start_rsp_tag, _handle_fwup_start, NULL);
  uc_route_register(fwpb_uxc_msg_device_fwup_transfer_rsp_tag, _handle_fwup_transfer, NULL);
  uc_route_register(fwpb_uxc_msg_device_fwup_finish_rsp_tag, _handle_fwup_finish, NULL);
}

bool fwup_task_send_coproc_fwup_start_cmd(fwpb_wallet_cmd* cmd) {
  ASSERT(cmd->msg.fwup_start_cmd.mcu_role == fwpb_mcu_role_MCU_ROLE_UXC);

  fwpb_uxc_msg_host* msg = uc_alloc_send_proto();
  ASSERT(msg != NULL);

  // Copy over the start command message.
  msg->which_msg = fwpb_uxc_msg_host_fwup_start_cmd_tag;
  msg->msg.fwup_start_cmd.mode = cmd->msg.fwup_start_cmd.mode;
  msg->msg.fwup_start_cmd.patch_size = cmd->msg.fwup_start_cmd.patch_size;
  msg->msg.fwup_start_cmd.mcu_role = cmd->msg.fwup_start_cmd.mcu_role;
  ipc_proto_free((uint8_t*)cmd);

  const bool sent = uc_send(msg);
  if (!sent) {
    // Force a failure response, as we were unable to send the FWUP start
    // command to the co-processor.
    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->which_msg = fwpb_wallet_rsp_fwup_start_rsp_tag;
    rsp->msg.fwup_start_rsp.rsp_status = fwpb_fwup_start_rsp_fwup_start_rsp_status_ERROR;
    proto_send_rsp(NULL, rsp);
  } else {
    fwup_mark_coproc_pending(true);
  }
  return sent;
}

void fwup_task_send_coproc_fwup_transfer_cmd(fwpb_wallet_cmd* cmd) {
  ASSERT(cmd->msg.fwup_transfer_cmd.mcu_role == fwpb_mcu_role_MCU_ROLE_UXC);

  fwpb_uxc_msg_host* msg = uc_alloc_send_proto();
  ASSERT(msg != NULL);

  // Copy over the transfer command message.
  msg->which_msg = fwpb_uxc_msg_host_fwup_transfer_cmd_tag;
  msg->msg.fwup_transfer_cmd.sequence_id = cmd->msg.fwup_transfer_cmd.sequence_id;
  msg->msg.fwup_transfer_cmd.offset = cmd->msg.fwup_transfer_cmd.offset;
  msg->msg.fwup_transfer_cmd.mode = cmd->msg.fwup_transfer_cmd.mode;
  msg->msg.fwup_transfer_cmd.mcu_role = cmd->msg.fwup_transfer_cmd.mcu_role;

  const size_t num_bytes = BLK_MIN(sizeof(msg->msg.fwup_transfer_cmd.fwup_data.bytes),
                                   cmd->msg.fwup_transfer_cmd.fwup_data.size);
  memcpy(msg->msg.fwup_transfer_cmd.fwup_data.bytes, cmd->msg.fwup_transfer_cmd.fwup_data.bytes,
         num_bytes);
  msg->msg.fwup_transfer_cmd.fwup_data.size = num_bytes;

  ipc_proto_free((uint8_t*)cmd);

  const bool sent = uc_send(msg);
  if (!sent) {
    // Force a failure response, as we were unable to send the FWUP transfer
    // command to the co-processor.
    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->which_msg = fwpb_wallet_rsp_fwup_transfer_rsp_tag;
    rsp->msg.fwup_transfer_rsp.rsp_status = fwpb_fwup_transfer_rsp_fwup_transfer_rsp_status_ERROR;
    proto_send_rsp(NULL, rsp);

    fwup_mark_coproc_pending(false);
  }
}

void fwup_task_send_coproc_fwup_finish_cmd(fwpb_wallet_cmd* cmd) {
  ASSERT(cmd->msg.fwup_finish_cmd.mcu_role == fwpb_mcu_role_MCU_ROLE_UXC);

  fwpb_uxc_msg_host* msg = uc_alloc_send_proto();
  ASSERT(msg != NULL);

  // Copy over the FWUP finish command message.
  msg->which_msg = fwpb_uxc_msg_host_fwup_finish_cmd_tag;
  msg->msg.fwup_finish_cmd.app_properties_offset = cmd->msg.fwup_finish_cmd.app_properties_offset;
  msg->msg.fwup_finish_cmd.signature_offset = cmd->msg.fwup_finish_cmd.signature_offset;
  msg->msg.fwup_finish_cmd.bl_upgrade = cmd->msg.fwup_finish_cmd.bl_upgrade;
  msg->msg.fwup_finish_cmd.mode = cmd->msg.fwup_finish_cmd.mode;
  msg->msg.fwup_finish_cmd.mcu_role = cmd->msg.fwup_finish_cmd.mcu_role;
  ipc_proto_free((uint8_t*)cmd);

  const bool sent = uc_send(msg);
  if (!sent) {
    // Force a failure response, as we were unable to send the FWUP finish
    // command to the co-processor.
    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->which_msg = fwpb_wallet_rsp_fwup_finish_rsp_tag;
    rsp->msg.fwup_finish_rsp.rsp_status = fwpb_fwup_finish_rsp_fwup_finish_rsp_status_ERROR;
    proto_send_rsp(NULL, rsp);

    fwup_mark_coproc_pending(false);
  }
}

void fwup_task_handle_coproc_fwup_start(ipc_ref_t* message) {
  ASSERT((message != NULL) && (message->object != NULL));

  fwpb_uxc_msg_device* msg_device = message->object;
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_fwup_start_rsp_tag;
  rsp->msg.fwup_start_rsp.rsp_status = msg_device->msg.fwup_start_rsp.rsp_status;
  rsp->msg.fwup_start_rsp.max_chunk_size = msg_device->msg.fwup_start_rsp.max_chunk_size;
  uc_free_recv_proto(msg_device);

  // Send the IPC message.
  proto_send_rsp(NULL, rsp);
}

void fwup_task_handle_coproc_fwup_transfer(ipc_ref_t* message) {
  ASSERT((message != NULL) && (message->object != NULL));

  fwpb_uxc_msg_device* msg_device = message->object;
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_fwup_transfer_rsp_tag;
  rsp->msg.fwup_transfer_rsp.rsp_status = msg_device->msg.fwup_transfer_rsp.rsp_status;
  uc_free_recv_proto(msg_device);

  // Send the IPC message.
  proto_send_rsp(NULL, rsp);
}

NO_OPTIMIZE void fwup_task_handle_coproc_fwup_finish(ipc_ref_t* message) {
  ASSERT((message != NULL) && (message->object != NULL));

  fwpb_uxc_msg_device* msg_device = message->object;
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_fwup_finish_rsp_tag;
  rsp->msg.fwup_finish_rsp.rsp_status = msg_device->msg.fwup_finish_rsp.rsp_status;
  uc_free_recv_proto(msg_device);

  // Store the status before free'ing.
  const bool success =
    ((rsp->msg.fwup_finish_rsp.rsp_status == fwpb_fwup_finish_rsp_fwup_finish_rsp_status_SUCCESS) ||
     (rsp->msg.fwup_finish_rsp.rsp_status ==
      fwpb_fwup_finish_rsp_fwup_finish_rsp_status_WILL_APPLY_PATCH));

  // Send the IPC message.
  proto_send_rsp(NULL, rsp);

  // Display the status for the firmware update.
  UI_SHOW_EVENT(UI_EVENT_LED_CLEAR);
  if (success) {
    UI_SHOW_EVENT(UI_EVENT_FWUP_COMPLETE);
  } else {
    UI_SHOW_EVENT(UI_EVENT_FWUP_FAILED);

    SECURE_DO({ deauthenticate_without_animation(); });
  }

  // Clear coprocessor FWUP flag after update completes
  fwup_mark_coproc_pending(false);
}

static void _handle_fwup_start(void* proto, void* UNUSED(context)) {
  ipc_send(fwup_port, proto, sizeof(proto), IPC_FWUP_START_COPROC_RSP);
}

static void _handle_fwup_transfer(void* proto, void* UNUSED(context)) {
  ipc_send(fwup_port, proto, sizeof(proto), IPC_FWUP_TRANSFER_COPROC_RSP);
}

static void _handle_fwup_finish(void* proto, void* UNUSED(context)) {
  ipc_send(fwup_port, proto, sizeof(proto), IPC_FWUP_FINISH_COPROC_RSP);
}
