#include "aes.h"
#include "confirmation_manager.h"
#include "eek_restoration_impl.h"
#include "log.h"
#include "proto_helpers.h"
#include "sealed_data_utils.h"
#include "secutils.h"
#include "sign_action_proof_core.h"
#include "ui_messaging.h"
#include "wallet.pb.h"
#include "wstring.h"

#include <string.h>

static SHARED_TASK_BSS sealed_unseal_session_t eek_session = {0};

void eek_restoration_unseal_symmetric_key_handle_init(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_eek_restoration_unseal_symmetric_key_rsp_tag;
  if (!sealed_unseal_begin(
        &cmd->msg.eek_restoration_unseal_symmetric_key_cmd.sealed_key, SAP_ACTION_EEK_RESTORATION,
        CONFIRMATION_TYPE_EEK_RESTORATION_UNSEAL_SYMMETRIC_KEY, &eek_session, rsp)) {
    LOGE("EEK restore: begin failed, status=%d", rsp->status);
  }
  proto_send_rsp(cmd, rsp);
}

static bool eek_restoration_unseal_symmetric_key_confirmation_result_handler(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  uint8_t unsealed_key[AES_256_LENGTH_BYTES] = {0};
  bool result = false;

  rsp->which_msg = fwpb_wallet_rsp_get_confirmation_result_rsp_tag;

  if (sealed_unseal_finish(&eek_session, cmd, rsp, unsealed_key)) {
    rsp->msg.get_confirmation_result_rsp.which_result =
      fwpb_get_confirmation_result_rsp_eek_restoration_unseal_symmetric_key_result_tag;
    memcpy(rsp->msg.get_confirmation_result_rsp.result.eek_restoration_unseal_symmetric_key_result
             .unsealed_key.bytes,
           unsealed_key, sizeof(unsealed_key));
    rsp->msg.get_confirmation_result_rsp.result.eek_restoration_unseal_symmetric_key_result
      .unsealed_key.size = sizeof(unsealed_key);
    ui_show_confirmation("Success", true);
    result = true;
  } else if (rsp->status != fwpb_status_CONFIRMATION_PENDING) {
    LOGE("EEK restore: result failed, status=%d", rsp->status);
  }

  memzero(unsealed_key, sizeof(unsealed_key));
  proto_send_rsp(cmd, rsp);
  return result;
}

void eek_restoration_register_handlers(void) {
  confirmation_manager_register_result_handler(
    CONFIRMATION_TYPE_EEK_RESTORATION_UNSEAL_SYMMETRIC_KEY,
    eek_restoration_unseal_symmetric_key_confirmation_result_handler);
}

void eek_restoration_clear_session(void) {
  sealed_unseal_clear_state(&eek_session);
}
