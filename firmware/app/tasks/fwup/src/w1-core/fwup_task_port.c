#include "assert.h"
#include "fwup.h"
#include "fwup_task_impl.h"
#include "proto_helpers.h"
#include "ui_messaging.h"
#include "wallet.pb.h"

#include <stdbool.h>
#include <stdint.h>

void fwup_task_register_listeners(void) {
  // No-op.
}

bool fwup_task_send_coproc_fwup_start_cmd(fwpb_wallet_cmd* UNUSED(cmd)) {
  // Should never be called on W1.
  ASSERT(false);
  return false;
}

void fwup_task_send_coproc_fwup_transfer_cmd(fwpb_wallet_cmd* UNUSED(cmd)) {
  // Should never be called on W1.
  ASSERT(false);
}

void fwup_task_send_coproc_fwup_finish_cmd(fwpb_wallet_cmd* UNUSED(cmd)) {
  // Should never be called on W1.
  ASSERT(false);
}

void fwup_task_handle_coproc_fwup_start(ipc_ref_t* UNUSED(message)) {
  // Should never be called on W1.
  ASSERT(false);
}

void fwup_task_handle_coproc_fwup_transfer(ipc_ref_t* UNUSED(message)) {
  // Should never be called on W1.
  ASSERT(false);
}

void fwup_task_handle_coproc_fwup_finish(ipc_ref_t* UNUSED(message)) {
  // Should never be called on W1.
  ASSERT(false);
}

bool fwup_handle_confirmation_result(ipc_ref_t* UNUSED(message)) {
  // W1 doesn't support confirmation flow
  return false;
}

bool fwup_task_port_handle_start_cmd(ipc_ref_t* message) {
  // Show LED to indicate FWUP start
  UI_SHOW_EVENT(UI_EVENT_FWUP_START);

  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_fwup_start_rsp_tag;

  bool result = fwup_start(&cmd->msg.fwup_start_cmd, &rsp->msg.fwup_start_rsp);
  fwup_mark_pending(result);
  proto_send_rsp(cmd, rsp);

  // Clear LED on failure
  if (!result) {
    UI_SHOW_EVENT(UI_EVENT_LED_CLEAR);
  }

  return result;
}
