#include "sealed_data_utils.h"

#include "aes.h"
#include "display.pb.h"
#include "log.h"
#include "proto_helpers.h"
#include "secutils.h"
#include "ui_events.h"
#include "ui_messaging.h"
#include "wallet.h"
#include "wstring.h"

#include <string.h>

bool sealed_data_unseal(const fwpb_sealed_data* sealed, uint8_t* output, size_t output_size) {
  if (sealed->data.size == 0 || sealed->data.size > output_size) {
    return false;
  }
  if (sealed->nonce.size != AES_GCM_IV_LENGTH) {
    return false;
  }
  if (sealed->tag.size != AES_GCM_TAG_LENGTH) {
    return false;
  }

  wallet_res_t result =
    wallet_csek_decrypt((uint8_t*)sealed->data.bytes, output, sealed->data.size,
                        (uint8_t*)sealed->nonce.bytes, (uint8_t*)sealed->tag.bytes);
  return (result == WALLET_RES_OK);
}

void sealed_unseal_clear_state(sealed_unseal_session_t* session) {
  confirmation_manager_clear();
  memzero(session, sizeof(*session));
}

bool sealed_unseal_begin(const fwpb_sealed_data* sealed_key, sap_action_t sap_action,
                         confirmation_type_t confirmation_type, sealed_unseal_session_t* session,
                         fwpb_wallet_rsp* rsp) {
  rsp->status = fwpb_status_ERROR;
  sealed_unseal_clear_state(session);

  if (sealed_key->data.size != AES_256_LENGTH_BYTES ||
      sealed_key->nonce.size != AES_GCM_IV_LENGTH || sealed_key->tag.size != AES_GCM_TAG_LENGTH) {
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    return false;
  }

  memcpy(&session->sealed_key, sealed_key, sizeof(fwpb_sealed_data));
  session->valid = true;

  uint8_t response_handle[CONFIRMATION_HANDLE_SIZE];
  uint8_t confirmation_handle[CONFIRMATION_HANDLE_SIZE];
  uint8_t token = 1;

  confirmation_result_t res = confirmation_manager_create(
    confirmation_type, &token, sizeof(token), response_handle, sizeof(response_handle),
    confirmation_handle, sizeof(confirmation_handle));
  if (res != CONFIRMATION_RESULT_SUCCESS) {
    sealed_unseal_clear_state(session);
    return false;
  }

  fwpb_display_params_privileged_action display_params = {0};
  display_params.sap_action = sap_action;
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
  return true;
}

bool sealed_unseal_finish(sealed_unseal_session_t* session, const fwpb_wallet_cmd* cmd,
                          fwpb_wallet_rsp* rsp, uint8_t unsealed_key_out[32]) {
  confirmation_result_t validation =
    confirmation_manager_validate(cmd->msg.get_confirmation_result_cmd.response_handle.bytes,
                                  cmd->msg.get_confirmation_result_cmd.response_handle.size,
                                  cmd->msg.get_confirmation_result_cmd.confirmation_handle.bytes,
                                  cmd->msg.get_confirmation_result_cmd.confirmation_handle.size);

  if (validation == CONFIRMATION_RESULT_NOT_APPROVED) {
    rsp->status = fwpb_status_CONFIRMATION_PENDING;
    return false;
  }
  if (validation != CONFIRMATION_RESULT_SUCCESS) {
    rsp->status = fwpb_status_CONFIRMATION_NOT_COMPLETED;
    sealed_unseal_clear_state(session);
    return false;
  }
  if (!session->valid) {
    rsp->status = fwpb_status_ERROR;
    sealed_unseal_clear_state(session);
    return false;
  }
  if (!sealed_data_unseal(&session->sealed_key, unsealed_key_out, AES_256_LENGTH_BYTES)) {
    rsp->status = fwpb_status_ERROR;
    sealed_unseal_clear_state(session);
    return false;
  }
  rsp->status = fwpb_status_SUCCESS;
  sealed_unseal_clear_state(session);
  return true;
}
