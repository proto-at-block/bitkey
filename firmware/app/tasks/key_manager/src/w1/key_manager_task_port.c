#include "assert.h"
#include "ipc.h"
#include "key_manager_task_impl.h"
#include "log.h"
#include "lost_app_recovery_impl.h"
#include "proto_helpers.h"
#include "wallet.h"
#include "wallet.pb.h"

void key_manager_task_handle_uxc_session_response(ipc_ref_t* message) {
  (void)message;
  LOGE("Unexpected UXC session rsp call");
}

void key_manager_task_handle_uxc_session_init(void) {
  LOGE("Unexpected UXC session init call");
}

void key_manager_task_port_handle_get_address(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_get_address_rsp_tag;
  rsp->status = fwpb_status_FEATURE_NOT_SUPPORTED;

  LOGE("getAddress not supported on W1");
  proto_send_rsp(cmd, rsp);
}

void key_manager_task_port_handle_verify_keys_and_build_descriptor(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_verify_keys_and_build_descriptor_rsp_tag;
  rsp->status = fwpb_status_FEATURE_NOT_SUPPORTED;

  LOGE("verify_keys_build_desc unsupported on W1");
  proto_send_rsp(cmd, rsp);
}

void key_manager_task_port_handle_derive_and_sign(ipc_ref_t* message) {
  handle_derive_and_sign(message);
}

void key_manager_task_port_handle_unseal_csek(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_unseal_csek_rsp_tag;

  if (sizeof(cmd->msg.unseal_csek_cmd.sealed_csek.data.bytes) !=
      cmd->msg.unseal_csek_cmd.sealed_csek.data.size) {
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    rsp->msg.unseal_csek_rsp.rsp_status = fwpb_unseal_csek_rsp_unseal_csek_rsp_status_ERROR;
    goto out;
  }
  _Static_assert(sizeof(cmd->msg.unseal_csek_cmd.sealed_csek.data.bytes) ==
                   sizeof(rsp->msg.unseal_csek_rsp.unsealed_csek.bytes),
                 "mismatch CSEK sizes");
  wallet_res_t result =
    wallet_csek_decrypt(cmd->msg.unseal_csek_cmd.sealed_csek.data.bytes,          // Wrapped CSEK
                        rsp->msg.unseal_csek_rsp.unsealed_csek.bytes,             // Raw CSEK out
                        sizeof(cmd->msg.unseal_csek_cmd.sealed_csek.data.bytes),  // CSEK size
                        cmd->msg.unseal_csek_cmd.sealed_csek.nonce.bytes,         // IV
                        cmd->msg.unseal_csek_cmd.sealed_csek.tag.bytes            // Tag
    );

  switch (result) {
    case WALLET_RES_OK:
      rsp->status = fwpb_status_SUCCESS;
      rsp->msg.unseal_csek_rsp.rsp_status = fwpb_unseal_csek_rsp_unseal_csek_rsp_status_SUCCESS;
      break;
    case WALLET_RES_UNSEALING_ERR:
      rsp->msg.unseal_csek_rsp.rsp_status =
        fwpb_unseal_csek_rsp_unseal_csek_rsp_status_UNSEAL_ERROR;
      goto out;
    default:
      rsp->msg.unseal_csek_rsp.rsp_status = fwpb_unseal_csek_rsp_unseal_csek_rsp_status_ERROR;
      goto out;
  }

  _Static_assert(CSEK_LENGTH == sizeof(rsp->msg.unseal_csek_rsp.unsealed_csek.bytes),
                 "mismatch CSEK sizes");
  rsp->msg.unseal_csek_rsp.unsealed_csek.size = CSEK_LENGTH;

out:
  proto_send_rsp(cmd, rsp);
}

void key_manager_task_register_listeners(void) {}

void key_manager_task_handle_sign_start(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_sign_start_rsp_tag;
  rsp->msg.sign_start_rsp.rsp_status = fwpb_sign_start_rsp_sign_start_rsp_status_ERROR;
  rsp->status = fwpb_status_FEATURE_NOT_SUPPORTED;

  LOGE("W1: chunked signing unsupported");
  proto_send_rsp(cmd, rsp);
}

void key_manager_task_handle_sign_transfer(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_sign_transfer_rsp_tag;
  rsp->status = fwpb_status_FEATURE_NOT_SUPPORTED;

  LOGE("W1: chunked signing unsupported");
  proto_send_rsp(cmd, rsp);
}

void key_manager_task_handle_get_confirmation_result_chunk(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_get_confirmation_result_chunk_rsp_tag;
  rsp->status = fwpb_status_FEATURE_NOT_SUPPORTED;

  LOGE("W1: chunked signing unsupported");
  proto_send_rsp(cmd, rsp);
}

void key_manager_task_handle_sign_tx_request(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_sign_tx_response_tag;
  rsp->status = fwpb_status_FEATURE_NOT_SUPPORTED;

  LOGE("W1 does not support non-PSBT transaction signing");
  proto_send_rsp(cmd, rsp);
}

void key_manager_task_try_deferred_sign(void) {
  LOGE("W1: chunked signing unsupported");
}

void key_manager_task_try_deferred_stream_sign(void) {
  LOGE("W1: streaming signing unsupported");
}

void key_manager_task_handle_sign_stream_start(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_sign_stream_start_rsp_tag;
  rsp->status = fwpb_status_FEATURE_NOT_SUPPORTED;

  LOGE("W1: streaming signing unsupported");
  proto_send_rsp(cmd, rsp);
}

void key_manager_task_handle_sign_stream_transfer(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_sign_stream_transfer_rsp_tag;
  rsp->status = fwpb_status_FEATURE_NOT_SUPPORTED;

  LOGE("W1: streaming signing unsupported");
  proto_send_rsp(cmd, rsp);
}

void key_manager_task_handle_sign_stream_finalize(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = 0;
  rsp->status = fwpb_status_FEATURE_NOT_SUPPORTED;

  LOGE("W1: streaming signing unsupported");
  proto_send_rsp(cmd, rsp);
}

void key_manager_task_handle_get_tx_signature(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_get_tx_signature_rsp_tag;
  rsp->status = fwpb_status_FEATURE_NOT_SUPPORTED;

  LOGE("W1: streaming signing unsupported");
  proto_send_rsp(cmd, rsp);
}

void key_manager_task_handle_get_tx_signatures_batch(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_get_tx_signatures_batch_rsp_tag;
  rsp->status = fwpb_status_FEATURE_NOT_SUPPORTED;

  LOGE("W1: streaming signing unsupported");
  proto_send_rsp(cmd, rsp);
}

void key_manager_task_try_sap_deferred_sign(void) {
  LOGE("W1 does not support sign action proof");
}

void key_manager_task_handle_sign_action_proof(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_sign_action_proof_rsp_tag;
  rsp->status = fwpb_status_FEATURE_NOT_SUPPORTED;

  LOGE("W1 does not support sign action proof");
  proto_send_rsp(cmd, rsp);
}

void key_manager_task_handle_lost_app_recovery(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->status = fwpb_status_FEATURE_NOT_SUPPORTED;

  LOGE("W1 does not support lost app recovery");
  proto_send_rsp(cmd, rsp);
}

void key_manager_task_handle_lost_app_recovery_continue(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_lost_app_recovery_continue_rsp_tag;
  rsp->status = fwpb_status_FEATURE_NOT_SUPPORTED;

  LOGE("W1 does not support lost app recovery");
  proto_send_rsp(cmd, rsp);
}

void key_manager_task_handle_lost_app_recovery_sign_challenge(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_lost_app_recovery_sign_challenge_rsp_tag;
  rsp->status = fwpb_status_FEATURE_NOT_SUPPORTED;

  LOGE("W1 does not support lost app recovery sign challenge");
  proto_send_rsp(cmd, rsp);
}

void key_manager_task_handle_rotate_app_auth_keys(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->status = fwpb_status_FEATURE_NOT_SUPPORTED;
  LOGE("W1 does not support rotate app auth keys");
  proto_send_rsp(cmd, rsp);
}

void key_manager_task_handle_upgrade_rotate_app_auth_keys(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->status = fwpb_status_FEATURE_NOT_SUPPORTED;
  LOGE("W1 does not support upgrade rotate app auth keys");
  proto_send_rsp(cmd, rsp);
}

void key_manager_task_handle_sign_challenge_and_seal_seks(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->status = fwpb_status_FEATURE_NOT_SUPPORTED;

  LOGE("W1 does not support sign challenge and seal seks");
  proto_send_rsp(cmd, rsp);
}

void key_manager_task_handle_recovery_authorize_lost_app(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->status = fwpb_status_FEATURE_NOT_SUPPORTED;

  LOGE("W1 does not support recovery authorize lost app");
  proto_send_rsp(cmd, rsp);
}

void key_manager_task_handle_recovery_authorize_lost_hw(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->status = fwpb_status_FEATURE_NOT_SUPPORTED;

  LOGE("W1 does not support recovery authorize lost hw");
  proto_send_rsp(cmd, rsp);
}

void key_manager_task_handle_upgrade_authorize_w3(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->status = fwpb_status_FEATURE_NOT_SUPPORTED;

  LOGE("W1 does not support upgrade authorize w3");
  proto_send_rsp(cmd, rsp);
}

void key_manager_task_handle_eek_restoration_unseal_symmetric_key(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_eek_restoration_unseal_symmetric_key_rsp_tag;
  rsp->status = fwpb_status_FEATURE_NOT_SUPPORTED;

  LOGE("W1 does not support EEK restoration unseal symmetric key");
  proto_send_rsp(cmd, rsp);
}

void key_manager_task_handle_full_account_cloud_backup_restoration(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_full_account_cloud_backup_restoration_rsp_tag;
  rsp->status = fwpb_status_FEATURE_NOT_SUPPORTED;

  LOGE("W1 does not support full account cloud backup restoration");
  proto_send_rsp(cmd, rsp);
}

void key_manager_task_handle_full_account_cloud_backup_restoration_continue(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_full_account_cloud_backup_restoration_continue_rsp_tag;
  rsp->status = fwpb_status_FEATURE_NOT_SUPPORTED;

  LOGE("W1 does not support full account cloud backup restoration continue");
  proto_send_rsp(cmd, rsp);
}
