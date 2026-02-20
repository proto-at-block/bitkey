#include "assert.h"
#include "auth.h"
#include "confirmation_manager.h"
#include "fwup.h"
#include "fwup_task_impl.h"
#include "ipc.h"
#include "log.h"
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

// Tracks whether we're awaiting an async UXC response to a get_confirmation_result command.
// Set to true when forwarding a confirmed UXC fwup_start to UXC over UART.
// Cleared when the UXC response arrives via _handle_fwup_start callback.
static SHARED_TASK_DATA bool awaiting_uxc_confirmation_response = false;

static bool fwup_confirmation_result_forwarder(ipc_ref_t* message) {
  ipc_send(fwup_port, message->object, message->length, IPC_FWUP_CONFIRMATION_RESULT);
  return true;
}

void fwup_task_register_listeners(void) {
  uc_route_register(fwpb_uxc_msg_device_fwup_start_rsp_tag, _handle_fwup_start, NULL);
  uc_route_register(fwpb_uxc_msg_device_fwup_transfer_rsp_tag, _handle_fwup_transfer, NULL);
  uc_route_register(fwpb_uxc_msg_device_fwup_finish_rsp_tag, _handle_fwup_finish, NULL);

  confirmation_manager_register_result_handler(CONFIRMATION_TYPE_FWUP_START,
                                               fwup_confirmation_result_forwarder);
}

NO_OPTIMIZE bool fwup_task_send_coproc_fwup_start_cmd(fwpb_wallet_cmd* cmd) {
  ASSERT(cmd->msg.fwup_start_cmd.mcu_role == fwpb_mcu_role_MCU_ROLE_UXC);

  SECURE_IF_FAILOUT(fwup_get_require_confirmation() == SECURE_FALSE) {
    // Mfgtest mode: Forward UXC command directly without on-device confirmation
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
  // If we reach here, prod mode: Require on-device confirmation for UXC update
  {
    uint8_t response_handle[32];
    uint8_t confirmation_handle[32];

    confirmation_result_t result = confirmation_manager_create(
      CONFIRMATION_TYPE_FWUP_START, &cmd->msg.fwup_start_cmd, sizeof(fwpb_fwup_start_cmd),
      response_handle, sizeof(response_handle), confirmation_handle, sizeof(confirmation_handle));

    if (result != CONFIRMATION_RESULT_SUCCESS) {
      LOGE("Failed to create UXC FWUP confirmation: %d", result);
      fwpb_wallet_rsp* rsp = proto_get_rsp();
      rsp->which_msg = fwpb_wallet_rsp_fwup_start_rsp_tag;
      rsp->msg.fwup_start_rsp.rsp_status = fwpb_fwup_start_rsp_fwup_start_rsp_status_ERROR;
      proto_send_rsp(cmd, rsp);
      return false;
    }

    // Show FWUP confirmation UI for UXC
    UI_SHOW_EVENT_WITH_DATA(UI_EVENT_FWUP_CONFIRMATION, &cmd->msg.fwup_start_cmd,
                            sizeof(fwpb_fwup_start_cmd));

    // Return CONFIRMATION_PENDING with handles
    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->status = fwpb_status_CONFIRMATION_PENDING;
    memcpy(rsp->response_handle.bytes, response_handle, sizeof(response_handle));
    rsp->response_handle.size = sizeof(response_handle);
    memcpy(rsp->confirmation_handle.bytes, confirmation_handle, sizeof(confirmation_handle));
    rsp->confirmation_handle.size = sizeof(confirmation_handle);

    proto_send_rsp(cmd, rsp);
    return true;
  }
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

  if (awaiting_uxc_confirmation_response) {
    // This is a response to get_confirmation_result for UXC
    awaiting_uxc_confirmation_response = false;

    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->which_msg = fwpb_wallet_rsp_get_confirmation_result_rsp_tag;
    rsp->msg.get_confirmation_result_rsp.which_result =
      fwpb_get_confirmation_result_rsp_fwup_start_result_tag;
    rsp->msg.get_confirmation_result_rsp.result.fwup_start_result.rsp_status =
      msg_device->msg.fwup_start_rsp.rsp_status;
    rsp->msg.get_confirmation_result_rsp.result.fwup_start_result.max_chunk_size =
      msg_device->msg.fwup_start_rsp.max_chunk_size;

    uc_free_recv_proto(msg_device);
    proto_send_rsp(NULL, rsp);
  } else {
    // Normal UXC FWUP start response (direct, not confirmation)
    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->which_msg = fwpb_wallet_rsp_fwup_start_rsp_tag;
    rsp->msg.fwup_start_rsp.rsp_status = msg_device->msg.fwup_start_rsp.rsp_status;
    rsp->msg.fwup_start_rsp.max_chunk_size = msg_device->msg.fwup_start_rsp.max_chunk_size;
    uc_free_recv_proto(msg_device);

    // Send the IPC message.
    proto_send_rsp(NULL, rsp);
  }
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

NO_OPTIMIZE bool fwup_task_port_handle_start_cmd(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);

  SECURE_IF_FAILOUT(fwup_get_require_confirmation() == SECURE_FALSE) {
    // Mfgtest mode: Skip 2-tap confirmation, execute directly
    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->which_msg = fwpb_wallet_rsp_fwup_start_rsp_tag;

    bool result = fwup_start(&cmd->msg.fwup_start_cmd, &rsp->msg.fwup_start_rsp);
    fwup_mark_pending(result);
    proto_send_rsp(cmd, rsp);

    return result;
  }
  // If we reach here, prod mode: Use confirmation flow
  {
    uint8_t response_handle[32];
    uint8_t confirmation_handle[32];

    // Create confirmation
    confirmation_result_t result = confirmation_manager_create(
      CONFIRMATION_TYPE_FWUP_START, &cmd->msg.fwup_start_cmd, sizeof(fwpb_fwup_start_cmd),
      response_handle, sizeof(response_handle), confirmation_handle, sizeof(confirmation_handle));

    if (result != CONFIRMATION_RESULT_SUCCESS) {
      LOGE("Failed to create confirmation: %d", result);
      return false;
    }

    // Show FWUP confirmation UI with command data
    UI_SHOW_EVENT_WITH_DATA(UI_EVENT_FWUP_CONFIRMATION, &cmd->msg.fwup_start_cmd,
                            sizeof(fwpb_fwup_start_cmd));

    // Return CONFIRMATION_PENDING with handles
    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->status = fwpb_status_CONFIRMATION_PENDING;
    memcpy(rsp->response_handle.bytes, response_handle, sizeof(response_handle));
    rsp->response_handle.size = sizeof(response_handle);
    memcpy(rsp->confirmation_handle.bytes, confirmation_handle, sizeof(confirmation_handle));
    rsp->confirmation_handle.size = sizeof(confirmation_handle);

    proto_send_rsp(cmd, rsp);
    return true;
  }
}

bool fwup_handle_confirmation_result(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);

  // Validate handles
  confirmation_result_t result =
    confirmation_manager_validate(cmd->msg.get_confirmation_result_cmd.response_handle.bytes,
                                  cmd->msg.get_confirmation_result_cmd.response_handle.size,
                                  cmd->msg.get_confirmation_result_cmd.confirmation_handle.bytes,
                                  cmd->msg.get_confirmation_result_cmd.confirmation_handle.size);

  if (result != CONFIRMATION_RESULT_SUCCESS) {
    // Check if this is "not approved yet" vs "invalid/expired/rejected"
    if (result == CONFIRMATION_RESULT_NOT_APPROVED && confirmation_manager_is_pending()) {
      // User hasn't approved yet (or rejected)
      fwpb_wallet_rsp* rsp = proto_get_rsp();
      rsp->status = fwpb_status_CONFIRMATION_PENDING;
      proto_send_rsp(cmd, rsp);
      return true;  // Not an error, just waiting for user
    } else {
      LOGE("Confirmation validation failed: %d", result);
      fwpb_wallet_rsp* rsp = proto_get_rsp();
      rsp->status = fwpb_status_ERROR;
      proto_send_rsp(cmd, rsp);
      return false;
    }
  }

  // Retrieve saved FWUP command from confirmation manager
  fwpb_fwup_start_cmd saved_cmd;
  size_t data_size;
  if (!confirmation_manager_get_operation_data(CONFIRMATION_TYPE_FWUP_START, &saved_cmd,
                                               &data_size)) {
    LOGE("Failed to retrieve FWUP command");
    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->status = fwpb_status_ERROR;
    proto_send_rsp(cmd, rsp);
    return false;
  }

  // Check if this is a UXC update confirmation
  if (saved_cmd.mcu_role == fwpb_mcu_role_MCU_ROLE_UXC) {
    // Forward to UXC over UART
    fwpb_uxc_msg_host* msg = uc_alloc_send_proto();
    if (msg == NULL) {
      LOGE("Failed to allocate UXC message");
      fwpb_wallet_rsp* rsp = proto_get_rsp();
      rsp->status = fwpb_status_ERROR;
      proto_send_rsp(cmd, rsp);
      confirmation_manager_clear();
      return false;
    }

    msg->which_msg = fwpb_uxc_msg_host_fwup_start_cmd_tag;
    msg->msg.fwup_start_cmd.mode = saved_cmd.mode;
    msg->msg.fwup_start_cmd.patch_size = saved_cmd.patch_size;
    msg->msg.fwup_start_cmd.mcu_role = saved_cmd.mcu_role;

    // Set flag to handle async UXC response
    awaiting_uxc_confirmation_response = true;

    const bool sent = uc_send(msg);
    if (!sent) {
      LOGE("Failed to send UXC FWUP command");
      awaiting_uxc_confirmation_response = false;
      fwpb_wallet_rsp* rsp = proto_get_rsp();
      rsp->status = fwpb_status_ERROR;
      proto_send_rsp(cmd, rsp);
      confirmation_manager_clear();
      return false;
    }

    fwup_mark_coproc_pending(true);

    // Show UI transition to in-progress
    UI_SHOW_EVENT(UI_EVENT_FWUP_START);

    // Clean up confirmation state
    confirmation_manager_clear();

    // Response will come asynchronously via _handle_fwup_start callback
    // Don't send response here - it will be sent from fwup_task_handle_coproc_fwup_start
    ipc_proto_free((uint8_t*)cmd);
    return true;
  } else {
    // Execute the saved FWUP start command (CORE MCU path)
    fwpb_wallet_rsp* rsp = proto_get_rsp();
    rsp->which_msg = fwpb_wallet_rsp_get_confirmation_result_rsp_tag;
    rsp->msg.get_confirmation_result_rsp.which_result =
      fwpb_get_confirmation_result_rsp_fwup_start_result_tag;

    bool fwup_success =
      fwup_start(&saved_cmd, &rsp->msg.get_confirmation_result_rsp.result.fwup_start_result);

    // Mark FWUP as pending if successful
    fwup_mark_pending(fwup_success);

    if (fwup_success) {
      // Transition UI from scanning to in-progress screen
      UI_SHOW_EVENT(UI_EVENT_FWUP_START);
    }

    // Clean up confirmation state
    confirmation_manager_clear();

    proto_send_rsp(cmd, rsp);
    return true;
  }
}
