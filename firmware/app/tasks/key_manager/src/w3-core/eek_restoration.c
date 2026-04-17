#include "aes.h"
#include "confirmation_manager.h"
#include "display.pb.h"
#include "eek_restoration_impl.h"
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
  fwpb_sealed_data sealed_key;
  bool valid;
} eek_restoration_unseal_symmetric_key_session_t;

static SHARED_TASK_BSS eek_restoration_unseal_symmetric_key_session_t
  eek_unseal_symmetric_key_session = {0};

static void eek_unseal_symmetric_key_session_clear(void) {
  memzero(&eek_unseal_symmetric_key_session, sizeof(eek_unseal_symmetric_key_session));
}

static void eek_unseal_symmetric_key_clear_state(void) {
  confirmation_manager_clear();
  eek_unseal_symmetric_key_session_clear();
}

void eek_restoration_unseal_symmetric_key_handle_init(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  const char* err = NULL;

  rsp->which_msg = fwpb_wallet_rsp_eek_restoration_unseal_symmetric_key_rsp_tag;
  rsp->status = fwpb_status_ERROR;

  eek_unseal_symmetric_key_clear_state();

  if (cmd->msg.eek_restoration_unseal_symmetric_key_cmd.sealed_key.data.size !=
        AES_256_LENGTH_BYTES ||
      cmd->msg.eek_restoration_unseal_symmetric_key_cmd.sealed_key.nonce.size !=
        AES_GCM_IV_LENGTH ||
      cmd->msg.eek_restoration_unseal_symmetric_key_cmd.sealed_key.tag.size != AES_GCM_TAG_LENGTH) {
    err = "invalid sealed key";
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }

  memcpy(&eek_unseal_symmetric_key_session.sealed_key,
         &cmd->msg.eek_restoration_unseal_symmetric_key_cmd.sealed_key, sizeof(fwpb_sealed_data));
  eek_unseal_symmetric_key_session.valid = true;

  uint8_t response_handle[CONFIRMATION_HANDLE_SIZE];
  uint8_t confirmation_handle[CONFIRMATION_HANDLE_SIZE];
  uint8_t token = 1;

  confirmation_result_t res = confirmation_manager_create(
    CONFIRMATION_TYPE_EEK_RESTORATION_UNSEAL_SYMMETRIC_KEY, &token, sizeof(token), response_handle,
    sizeof(response_handle), confirmation_handle, sizeof(confirmation_handle));

  if (res != CONFIRMATION_RESULT_SUCCESS) {
    err = "CM create failed";
    eek_unseal_symmetric_key_session_clear();
    goto out;
  }

  fwpb_display_params_privileged_action display_params = {0};
  display_params.sap_action = SAP_ACTION_EEK_RESTORATION;
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

out:
  if (err) {
    LOGE("EEK restore: %s", err);
  }
  proto_send_rsp(cmd, rsp);
}

static bool eek_restoration_unseal_symmetric_key_confirmation_result_handler(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  const char* err = NULL;
  bool result = true;
  uint8_t unsealed_key[AES_256_LENGTH_BYTES] = {0};

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
    goto cleanup;
  }

  if (!eek_unseal_symmetric_key_session.valid) {
    err = "no session";
    rsp->status = fwpb_status_ERROR;
    result = false;
    goto cleanup;
  }

  if (!sealed_data_unseal(&eek_unseal_symmetric_key_session.sealed_key, unsealed_key,
                          sizeof(unsealed_key))) {
    err = "unseal failed";
    rsp->status = fwpb_status_ERROR;
    result = false;
    goto cleanup;
  }

  rsp->msg.get_confirmation_result_rsp.which_result =
    fwpb_get_confirmation_result_rsp_eek_restoration_unseal_symmetric_key_result_tag;
  memcpy(rsp->msg.get_confirmation_result_rsp.result.eek_restoration_unseal_symmetric_key_result
           .unsealed_key.bytes,
         unsealed_key, sizeof(unsealed_key));
  rsp->msg.get_confirmation_result_rsp.result.eek_restoration_unseal_symmetric_key_result
    .unsealed_key.size = sizeof(unsealed_key);
  rsp->status = fwpb_status_SUCCESS;

cleanup:
  memzero(unsealed_key, sizeof(unsealed_key));
  eek_unseal_symmetric_key_clear_state();

out:
  if (err) {
    LOGE("EEK restore: %s", err);
  }
  if (rsp->status == fwpb_status_SUCCESS) {
    ui_show_confirmation("Success", true);
  }
  proto_send_rsp(cmd, rsp);
  return result;
}

void eek_restoration_register_handlers(void) {
  confirmation_manager_register_result_handler(
    CONFIRMATION_TYPE_EEK_RESTORATION_UNSEAL_SYMMETRIC_KEY,
    eek_restoration_unseal_symmetric_key_confirmation_result_handler);
}

void eek_restoration_clear_session(void) {
  eek_unseal_symmetric_key_clear_state();
}
