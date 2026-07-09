#include "aes.h"
#include "bip32.h"
#include "confirmation_manager.h"
#include "display.pb.h"
#include "hash.h"
#include "ipc.h"
#include "key_manager_task_impl.h"
#include "keyset_repair_impl.h"
#include "log.h"
#include "proto_helpers.h"
#include "sealed_data_utils.h"
#include "secutils.h"
#include "sign_action_proof_core.h"
#include "ui_events.h"
#include "ui_messaging.h"
#include "wallet.h"
#include "wallet.pb.h"
#include "wstring.h"

#include <string.h>

// ---------------------------------------------------------------------------
// Tap 1: keyset_repair_unseal_symmetric_key
//
// Implementation lives in sealed_data_utils.c via sealed_unseal_{begin,finish}.
// ---------------------------------------------------------------------------

static SHARED_TASK_BSS sealed_unseal_session_t keyset_repair_unseal_session = {0};

void keyset_repair_unseal_symmetric_key_handle_init(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  if (!sealed_unseal_begin(&cmd->msg.keyset_repair_unseal_symmetric_key_cmd.sealed_key,
                           SAP_ACTION_KEYSET_REPAIR_UNSEAL,
                           CONFIRMATION_TYPE_KEYSET_REPAIR_UNSEAL_SYMMETRIC_KEY,
                           &keyset_repair_unseal_session, rsp)) {
    LOGE("keyset repair unseal: begin failed, status=%d", rsp->status);
  }
  proto_send_rsp(cmd, rsp);
}

static bool keyset_repair_unseal_confirmation_result_handler(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  uint8_t unsealed_key[AES_256_LENGTH_BYTES] = {0};
  bool result = false;

  rsp->which_msg = fwpb_wallet_rsp_get_confirmation_result_rsp_tag;

  if (sealed_unseal_finish(&keyset_repair_unseal_session, cmd, rsp, unsealed_key)) {
    rsp->msg.get_confirmation_result_rsp.which_result =
      fwpb_get_confirmation_result_rsp_keyset_repair_unseal_symmetric_key_result_tag;
    memcpy(rsp->msg.get_confirmation_result_rsp.result.keyset_repair_unseal_symmetric_key_result
             .unsealed_key.bytes,
           unsealed_key, sizeof(unsealed_key));
    rsp->msg.get_confirmation_result_rsp.result.keyset_repair_unseal_symmetric_key_result
      .unsealed_key.size = sizeof(unsealed_key);
    ui_show_confirmation("Success", false);  // Don't lock -- keyset repair has a second tap.
    result = true;
  } else if (rsp->status != fwpb_status_CONFIRMATION_PENDING) {
    LOGE("keyset repair unseal: result failed, status=%d", rsp->status);
  }

  memzero(unsealed_key, sizeof(unsealed_key));
  proto_send_rsp(cmd, rsp);
  return result;
}

// ---------------------------------------------------------------------------
// Tap 2: keyset_repair_rotate_hw_key
//
// Composite — derives next spending key + signs access token hash in one confirmable tap.
// ---------------------------------------------------------------------------

typedef struct {
  uint8_t access_token_hash[SHA256_DIGEST_SIZE];
  uint32_t next_account_index;
  fwpb_btc_network network;
  bool valid;
} keyset_repair_rotate_session_t;

static SHARED_TASK_BSS keyset_repair_rotate_session_t keyset_repair_rotate_session = {0};

static void keyset_repair_rotate_clear_state(void) {
  confirmation_manager_clear();
  memzero(&keyset_repair_rotate_session, sizeof(keyset_repair_rotate_session));
}

static uint32_t sign_status_to_fwpb(key_manager_sign_result_t r) {
  switch (r) {
    case KEY_MANAGER_SIGN_SUCCESS:
      return fwpb_status_SUCCESS;
    case KEY_MANAGER_SIGN_DERIVATION_FAILED:
      return fwpb_status_KEY_DERIVATION_FAILED;
    case KEY_MANAGER_SIGN_POLICY_VIOLATION:
      return fwpb_status_INVALID_ARGUMENT;
    default:
      return fwpb_status_SIGNING_FAILED;
  }
}

void keyset_repair_rotate_hw_key_handle_init(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->status = fwpb_status_ERROR;

  keyset_repair_rotate_clear_state();

  size_t access_token_hash_size = cmd->msg.keyset_repair_rotate_hw_key_cmd.access_token_hash.size;
  if (access_token_hash_size != SHA256_DIGEST_SIZE) {
    LOGE("keyset repair rotate: invalid access token hash size=%zu", access_token_hash_size);
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    proto_send_rsp(cmd, rsp);
    return;
  }

  memcpy(keyset_repair_rotate_session.access_token_hash,
         cmd->msg.keyset_repair_rotate_hw_key_cmd.access_token_hash.bytes, SHA256_DIGEST_SIZE);
  keyset_repair_rotate_session.next_account_index =
    cmd->msg.keyset_repair_rotate_hw_key_cmd.next_account_index;
  keyset_repair_rotate_session.network = cmd->msg.keyset_repair_rotate_hw_key_cmd.network;
  keyset_repair_rotate_session.valid = true;

  uint8_t response_handle[CONFIRMATION_HANDLE_SIZE];
  uint8_t confirmation_handle[CONFIRMATION_HANDLE_SIZE];
  uint8_t token = 1;

  if (confirmation_manager_create(CONFIRMATION_TYPE_KEYSET_REPAIR_ROTATE_HW_KEY, &token,
                                  sizeof(token), response_handle, sizeof(response_handle),
                                  confirmation_handle,
                                  sizeof(confirmation_handle)) != CONFIRMATION_RESULT_SUCCESS) {
    LOGE("keyset repair rotate: CM create failed");
    keyset_repair_rotate_clear_state();
    proto_send_rsp(cmd, rsp);
    return;
  }

  fwpb_display_params_privileged_action display_params = {0};
  display_params.sap_action = SAP_ACTION_KEYSET_REPAIR_ROTATE;
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
  proto_send_rsp(cmd, rsp);
}

static bool keyset_repair_rotate_confirmation_result_handler(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_get_confirmation_result_rsp_tag;

  confirmation_result_t validation =
    confirmation_manager_validate(cmd->msg.get_confirmation_result_cmd.response_handle.bytes,
                                  cmd->msg.get_confirmation_result_cmd.response_handle.size,
                                  cmd->msg.get_confirmation_result_cmd.confirmation_handle.bytes,
                                  cmd->msg.get_confirmation_result_cmd.confirmation_handle.size);
  if (validation == CONFIRMATION_RESULT_NOT_APPROVED) {
    rsp->status = fwpb_status_CONFIRMATION_PENDING;
    proto_send_rsp(cmd, rsp);
    return false;
  }
  if (validation != CONFIRMATION_RESULT_SUCCESS) {
    rsp->status = fwpb_status_CONFIRMATION_NOT_COMPLETED;
    LOGE("keyset repair rotate: confirmation validation failed=%d", validation);
    keyset_repair_rotate_clear_state();
    proto_send_rsp(cmd, rsp);
    return false;
  }
  if (!keyset_repair_rotate_session.valid) {
    rsp->status = fwpb_status_ERROR;
    LOGE("keyset repair rotate: missing valid session");
    keyset_repair_rotate_clear_state();
    proto_send_rsp(cmd, rsp);
    return false;
  }

  fwpb_keyset_repair_rotate_hw_key_rsp* rotate_result =
    &rsp->msg.get_confirmation_result_rsp.result.keyset_repair_rotate_hw_key_result;
  memzero(rotate_result, sizeof(*rotate_result));

  // 1. Derive the next HW spending key.
  bool is_mainnet = (keyset_repair_rotate_session.network == fwpb_btc_network_BITCOIN);
  uint32_t spending_indices[] = {
    BIP84_PURPOSE | BIP32_HARDENED_BIT,
    (is_mainnet ? BIP32_COIN_BTC : BIP32_COIN_TESTNET) | BIP32_HARDENED_BIT,
    keyset_repair_rotate_session.next_account_index | BIP32_HARDENED_BIT,
  };
  derivation_path_t spending_path = {
    .indices = spending_indices,
    .num_indices = BIP32_PATH_DEPTH_ACCOUNT,
  };

  if (!key_manager_derive_key_descriptor(spending_path, is_mainnet ? MAINNET_PUB : TESTNET_PUB,
                                         &rotate_result->spending_key_descriptor, NULL)) {
    LOGE("keyset repair rotate: derive failed at index=%lu",
         (unsigned long)keyset_repair_rotate_session.next_account_index);
    rsp->status = fwpb_status_KEY_DERIVATION_FAILED;
    keyset_repair_rotate_clear_state();
    proto_send_rsp(cmd, rsp);
    return false;
  }
  rotate_result->has_spending_key_descriptor = true;

  // 2. Sign sha256(access token) with the HW auth key. The app/Rust layer computes
  //    this digest so NFC never needs to carry the full JWT.
  rsp->status = sign_status_to_fwpb(key_manager_derive_and_sign(
    *wallet_get_w1_auth_path(), keyset_repair_rotate_session.access_token_hash,
    rotate_result->access_token_signature.bytes));

  bool ok = (rsp->status == fwpb_status_SUCCESS);
  if (ok) {
    rsp->msg.get_confirmation_result_rsp.which_result =
      fwpb_get_confirmation_result_rsp_keyset_repair_rotate_hw_key_result_tag;
    rotate_result->access_token_signature.size = ECC_SIG_SIZE;
    ui_show_confirmation("Success", false);  // Don't lock -- descriptor provisioning follows.
  } else {
    LOGE("keyset repair rotate: sign failed, status=%d", rsp->status);
  }
  keyset_repair_rotate_clear_state();
  proto_send_rsp(cmd, rsp);
  return ok;
}

// ---------------------------------------------------------------------------
// Registration / lifecycle
// ---------------------------------------------------------------------------

void keyset_repair_register_handlers(void) {
  confirmation_manager_register_result_handler(CONFIRMATION_TYPE_KEYSET_REPAIR_UNSEAL_SYMMETRIC_KEY,
                                               keyset_repair_unseal_confirmation_result_handler);
  confirmation_manager_register_result_handler(CONFIRMATION_TYPE_KEYSET_REPAIR_ROTATE_HW_KEY,
                                               keyset_repair_rotate_confirmation_result_handler);
}

void keyset_repair_clear_session(void) {
  sealed_unseal_clear_state(&keyset_repair_unseal_session);
  keyset_repair_rotate_clear_state();
}
