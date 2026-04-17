#include "aes.h"
#include "confirmation_manager.h"
#include "display.pb.h"
#include "full_account_cloud_backup_restoration_impl.h"
#include "ipc.h"
#include "log.h"
#include "proto_helpers.h"
#include "sealed_data_utils.h"
#include "secutils.h"
#include "sign_action_proof_core.h"
#include "ui_events.h"
#include "ui_messaging.h"
#include "wallet.pb.h"
#include "wstring.h"

#include <string.h>

typedef struct {
  bool valid;
  bool confirmed;
  uint8_t session_token[CONFIRMATION_HANDLE_SIZE];
} full_account_cloud_backup_restoration_session_t;

static SHARED_TASK_BSS full_account_cloud_backup_restoration_session_t facbr_session = {0};

static void facbr_session_clear(void) {
  memzero(&facbr_session, sizeof(facbr_session));
}

void full_account_cloud_backup_restoration_handle_init(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  const char* err = NULL;

  rsp->which_msg = fwpb_wallet_rsp_full_account_cloud_backup_restoration_rsp_tag;
  rsp->status = fwpb_status_ERROR;

  confirmation_manager_clear();
  facbr_session_clear();
  facbr_session.valid = true;

  uint8_t response_handle[CONFIRMATION_HANDLE_SIZE];
  uint8_t confirmation_handle[CONFIRMATION_HANDLE_SIZE];
  uint8_t token = 1;

  confirmation_result_t res = confirmation_manager_create(
    CONFIRMATION_TYPE_FULL_ACCOUNT_CLOUD_BACKUP_RESTORATION, &token, sizeof(token), response_handle,
    sizeof(response_handle), confirmation_handle, sizeof(confirmation_handle));

  if (res != CONFIRMATION_RESULT_SUCCESS) {
    err = "CM create failed";
    facbr_session_clear();
    goto out;
  }

  fwpb_display_params_privileged_action display_params = {0};
  display_params.sap_action = SAP_ACTION_FULL_ACCOUNT_CLOUD_BACKUP_RESTORE;
  display_params.which_action = fwpb_display_params_privileged_action_confirm_action_tag;
  display_params.action.confirm_action.action_type =
    fwpb_display_privileged_action_type_DISPLAY_PRIVILEGED_ACTION_NONE;
  UI_SHOW_EVENT_WITH_DATA(UI_EVENT_START_PRIVILEGED_ACTION, &display_params,
                          sizeof(display_params));

  rsp->status = fwpb_status_CONFIRMATION_PENDING;
  memcpy(rsp->response_handle.bytes, response_handle, sizeof(response_handle));
  rsp->response_handle.size = sizeof(response_handle);
  memcpy(rsp->confirmation_handle.bytes, confirmation_handle, sizeof(confirmation_handle));
  rsp->confirmation_handle.size = sizeof(confirmation_handle);

  // Store response_handle as session binding token so _continue commands
  // can prove they belong to this confirmed session.
  memcpy(facbr_session.session_token, response_handle, sizeof(response_handle));

out:
  if (err) {
    LOGE("FACBR: %s", err);
  }
  proto_send_rsp(cmd, rsp);
}

static bool full_account_cloud_backup_restoration_confirmation_result_handler(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  const char* err = NULL;
  bool result = true;

  rsp->which_msg = fwpb_wallet_rsp_get_confirmation_result_rsp_tag;

  confirmation_result_t validation =
    confirmation_manager_validate(cmd->msg.get_confirmation_result_cmd.response_handle.bytes,
                                  cmd->msg.get_confirmation_result_cmd.response_handle.size,
                                  cmd->msg.get_confirmation_result_cmd.confirmation_handle.bytes,
                                  cmd->msg.get_confirmation_result_cmd.confirmation_handle.size);

  if (validation == CONFIRMATION_RESULT_NOT_APPROVED) {
    rsp->status = fwpb_status_CONFIRMATION_PENDING;
    goto out;
  }

  if (validation != CONFIRMATION_RESULT_SUCCESS) {
    err = "confirm failed";
    rsp->status = fwpb_status_CONFIRMATION_NOT_COMPLETED;
    result = false;
    facbr_session_clear();
    confirmation_manager_clear();
    goto out;
  }

  if (!facbr_session.valid) {
    err = "no session";
    rsp->status = fwpb_status_ERROR;
    result = false;
    confirmation_manager_clear();
    goto out;
  }

  facbr_session.confirmed = true;
  rsp->msg.get_confirmation_result_rsp.which_result =
    fwpb_get_confirmation_result_rsp_full_account_cloud_backup_restoration_result_tag;
  rsp->status = fwpb_status_SUCCESS;

out:
  if (err) {
    LOGE("FACBR: %s", err);
  }
  proto_send_rsp(cmd, rsp);
  return result;
}

bool full_account_cloud_backup_restoration_is_session_ready(void) {
  return facbr_session.valid && facbr_session.confirmed;
}

void full_account_cloud_backup_restoration_handle_continue(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  const char* err = NULL;
  uint8_t unsealed_csek[AES_256_LENGTH_BYTES] = {0};

  rsp->which_msg = fwpb_wallet_rsp_full_account_cloud_backup_restoration_continue_rsp_tag;
  rsp->status = fwpb_status_ERROR;

  if (!full_account_cloud_backup_restoration_is_session_ready()) {
    err = "session not ready";
    rsp->status = fwpb_status_INVALID_STATE;
    goto out;
  }

  // Verify session binding: the caller must echo the response_handle from init.
  if (cmd->msg.full_account_cloud_backup_restoration_continue_cmd.session_token.size !=
        CONFIRMATION_HANDLE_SIZE ||
      memcmp_s(cmd->msg.full_account_cloud_backup_restoration_continue_cmd.session_token.bytes,
               facbr_session.session_token, CONFIRMATION_HANDLE_SIZE) != 0) {
    err = "session token mismatch";
    rsp->status = fwpb_status_CONFIRMATION_MISMATCH;
    facbr_session_clear();
    confirmation_manager_clear();
    goto out;
  }

  if (!sealed_data_unseal(&cmd->msg.full_account_cloud_backup_restoration_continue_cmd.sealed_csek,
                          unsealed_csek, sizeof(unsealed_csek))) {
    err = "unseal failed";
    rsp->status = fwpb_status_SEAL_CSEK_UNSEAL_FAILED;
    goto out;
  }

  memcpy(rsp->msg.full_account_cloud_backup_restoration_continue_rsp.unsealed_csek.bytes,
         unsealed_csek, sizeof(unsealed_csek));
  rsp->msg.full_account_cloud_backup_restoration_continue_rsp.unsealed_csek.size =
    sizeof(unsealed_csek);
  rsp->msg.full_account_cloud_backup_restoration_continue_rsp.csek_index =
    cmd->msg.full_account_cloud_backup_restoration_continue_cmd.csek_index;
  rsp->status = fwpb_status_SUCCESS;

out:
  memzero(unsealed_csek, sizeof(unsealed_csek));
  // Clear session after successful unseal — the app got what it needed.
  // Unseal failures and invalid-argument errors leave the session alive so the
  // app can retry with a different CSEK in the same NFC session.
  if (rsp->status == fwpb_status_SUCCESS) {
    ui_show_confirmation("Success", false);  // Don't lock -- provisionAppAuthKey follows
    facbr_session_clear();
    confirmation_manager_clear();
  }
  if (err) {
    LOGE("FACBR: %s", err);
  }
  proto_send_rsp(cmd, rsp);
}

void full_account_cloud_backup_restoration_register_handlers(void) {
  confirmation_manager_register_result_handler(
    CONFIRMATION_TYPE_FULL_ACCOUNT_CLOUD_BACKUP_RESTORATION,
    full_account_cloud_backup_restoration_confirmation_result_handler);
}

void full_account_cloud_backup_restoration_clear_session(void) {
  facbr_session_clear();
  confirmation_manager_clear();
}
