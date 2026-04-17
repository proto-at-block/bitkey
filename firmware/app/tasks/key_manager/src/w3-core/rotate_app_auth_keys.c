#include "bip32.h"
#include "confirmation_manager.h"
#include "display.pb.h"
#include "ecc.h"
#include "hash.h"
#include "ipc.h"
#include "key_manager_task_impl.h"
#include "log.h"
#include "proto_helpers.h"
#include "rotate_app_auth_keys_impl.h"
#include "secutils.h"
#include "seed.h"
#include "sign_action_proof_core.h"
#include "ui_events.h"
#include "ui_messaging.h"
#include "wallet.h"
#include "wallet.pb.h"
#include "wstring.h"

#include <string.h>

// Session state for rotate app auth keys (persists between init and confirmation)
typedef struct {
  fwpb_rotate_app_auth_keys_cmd cmd_data;
  bool valid;
} rotate_app_auth_keys_session_t;

static SHARED_TASK_BSS rotate_app_auth_keys_session_t raak_session = {0};

static void raak_session_clear(void) {
  memzero(&raak_session, sizeof(raak_session));
}

// ---------------------------------------------------------------------------
// Confirmation result handler
// ---------------------------------------------------------------------------
static bool raak_confirmation_result_handler(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_get_confirmation_result_rsp_tag;

  // Validate handles
  confirmation_result_t validation =
    confirmation_manager_validate(cmd->msg.get_confirmation_result_cmd.response_handle.bytes,
                                  cmd->msg.get_confirmation_result_cmd.response_handle.size,
                                  cmd->msg.get_confirmation_result_cmd.confirmation_handle.bytes,
                                  cmd->msg.get_confirmation_result_cmd.confirmation_handle.size);

  if (validation == CONFIRMATION_RESULT_NOT_APPROVED) {
    rsp->status = fwpb_status_CONFIRMATION_PENDING;
    proto_send_rsp(cmd, rsp);
    return true;
  }

  if (validation != CONFIRMATION_RESULT_SUCCESS) {
    rsp->status = fwpb_status_CONFIRMATION_NOT_COMPLETED;
    LOGE("RAAK confirm fail: %d", validation);
    raak_session_clear();
    confirmation_manager_clear();
    proto_send_rsp(cmd, rsp);
    return false;
  }

  if (!raak_session.valid) {
    rsp->status = fwpb_status_ERROR;
    LOGE("RAAK no session");
    confirmation_manager_clear();
    proto_send_rsp(cmd, rsp);
    return false;
  }

  rsp->msg.get_confirmation_result_rsp.which_result =
    fwpb_get_confirmation_result_rsp_rotate_app_auth_keys_rsp_tag;
  fwpb_rotate_app_auth_keys_rsp* raak_rsp =
    &rsp->msg.get_confirmation_result_rsp.result.rotate_app_auth_keys_rsp;

  derivation_path_t* auth_path = wallet_get_w1_auth_path();

  // 1. Sign ActionProof payload with HW auth key
  if (raak_session.cmd_data.action_proof_version != 1) {
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }
  sap_action_t action = sap_parse_action(raak_session.cmd_data.action);
  if (action == SAP_ACTION_COUNT) {
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }
  if (action != SAP_ACTION_ROTATE_APP_AUTH_KEYS) {
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }
  {
    sap_session_t raak_sap = {0};
    sap_session_init(&raak_sap);
    raak_sap.pending_data.valid = true;
    raak_sap.pending_data.version = raak_session.cmd_data.action_proof_version;

    if (strlen(raak_session.cmd_data.action) >= sizeof(raak_sap.pending_data.action) ||
        strlen(raak_session.cmd_data.value) >= sizeof(raak_sap.pending_data.value) ||
        strlen(raak_session.cmd_data.bindings) >= sizeof(raak_sap.pending_data.bindings)) {
      rsp->status = fwpb_status_INVALID_ARGUMENT;
      goto out;
    }

    strncpy(raak_sap.pending_data.action, raak_session.cmd_data.action,
            sizeof(raak_sap.pending_data.action) - 1);
    strncpy(raak_sap.pending_data.value, raak_session.cmd_data.value,
            sizeof(raak_sap.pending_data.value) - 1);
    strncpy(raak_sap.pending_data.bindings, raak_session.cmd_data.bindings,
            sizeof(raak_sap.pending_data.bindings) - 1);

    int sign_status = sap_sign(&raak_sap);
    if (sign_status != fwpb_status_SUCCESS) {
      rsp->status = sign_status;
      goto out;
    }
    memcpy(raak_rsp->action_proof_signature.bytes, raak_sap.signature, ECC_SIG_SIZE);
    raak_rsp->action_proof_signature.size = ECC_SIG_SIZE;
  }

  // 2. Sign account_id with domain separation: SHA-256("BKAuthRotation" || account_id)
  {
    uint8_t hash[SHA256_DIGEST_SIZE] = {0};
    size_t account_id_len = strlen(raak_session.cmd_data.account_id);
    if (account_id_len == 0) {
      rsp->status = fwpb_status_INVALID_ARGUMENT;
      goto out;
    }
#define RAAK_ACCT_DOMAIN_TAG "BKAuthRotation"
    hash_stream_ctx_t ctx;
    if (!crypto_sha256_stream_init(&ctx) ||
        !crypto_sha256_stream_update(&ctx, (uint8_t*)RAAK_ACCT_DOMAIN_TAG,
                                     sizeof(RAAK_ACCT_DOMAIN_TAG) - 1) ||
        !crypto_sha256_stream_update(&ctx, (uint8_t*)raak_session.cmd_data.account_id,
                                     (uint32_t)account_id_len) ||
        !crypto_sha256_stream_final(&ctx, hash)) {
      rsp->status = fwpb_status_ERROR;
      goto out;
    }
#undef RAAK_ACCT_DOMAIN_TAG
    key_manager_sign_result_t sign_res =
      key_manager_derive_and_sign(*auth_path, hash, raak_rsp->hw_signed_account_id.bytes);
    memzero(hash, sizeof(hash));
    switch (sign_res) {
      case KEY_MANAGER_SIGN_SUCCESS:
        break;
      case KEY_MANAGER_SIGN_DERIVATION_FAILED:
        rsp->status = fwpb_status_KEY_DERIVATION_FAILED;
        goto out;
      case KEY_MANAGER_SIGN_POLICY_VIOLATION:
        rsp->status = fwpb_status_INVALID_ARGUMENT;
        goto out;
      default:
        rsp->status = fwpb_status_SIGNING_FAILED;
        goto out;
    }
    raak_rsp->hw_signed_account_id.size = ECC_SIG_SIZE;
  }

  // 3. Sign app_global_auth_key with domain separation:
  //    SHA-256("BKRelationshipEndorsement" || hex_pubkey_string)
  //    The key is sent as a hex-encoded string (e.g. "02abcd...") to match
  //    the existing signChallenge convention used by RelationshipsCryptoImpl.
  {
    uint8_t hash[SHA256_DIGEST_SIZE] = {0};
    size_t app_key_len = strlen(raak_session.cmd_data.app_global_auth_key);
    if (app_key_len == 0) {
      rsp->status = fwpb_status_INVALID_ARGUMENT;
      goto out;
    }
#define RAAK_KEY_DOMAIN_TAG "BKRelationshipEndorsement"
    hash_stream_ctx_t ctx;
    if (!crypto_sha256_stream_init(&ctx) ||
        !crypto_sha256_stream_update(&ctx, (uint8_t*)RAAK_KEY_DOMAIN_TAG,
                                     sizeof(RAAK_KEY_DOMAIN_TAG) - 1) ||
        !crypto_sha256_stream_update(&ctx, (uint8_t*)raak_session.cmd_data.app_global_auth_key,
                                     (uint32_t)app_key_len) ||
        !crypto_sha256_stream_final(&ctx, hash)) {
      rsp->status = fwpb_status_ERROR;
      goto out;
    }
#undef RAAK_KEY_DOMAIN_TAG
    key_manager_sign_result_t sign_res =
      key_manager_derive_and_sign(*auth_path, hash, raak_rsp->app_auth_key_signature.bytes);
    memzero(hash, sizeof(hash));
    switch (sign_res) {
      case KEY_MANAGER_SIGN_SUCCESS:
        break;
      case KEY_MANAGER_SIGN_DERIVATION_FAILED:
        rsp->status = fwpb_status_KEY_DERIVATION_FAILED;
        goto out;
      case KEY_MANAGER_SIGN_POLICY_VIOLATION:
        rsp->status = fwpb_status_INVALID_ARGUMENT;
        goto out;
      default:
        rsp->status = fwpb_status_SIGNING_FAILED;
        goto out;
    }
    raak_rsp->app_auth_key_signature.size = ECC_SIG_SIZE;
  }

  // 4. Derive and return HW auth public key (33-byte compressed)
  {
    extended_key_t key_priv __attribute__((__cleanup__(bip32_zero_key)));
    fingerprint_t master_fp;
    fingerprint_t parent_fp;

    if (seed_derive_bip32(*auth_path, &key_priv, &master_fp, &parent_fp) != SEED_RES_OK) {
      LOGE("RAAK: key deriv fail");
      rsp->status = fwpb_status_KEY_DERIVATION_FAILED;
      goto out;
    }

    extended_key_t key_pub __attribute__((__cleanup__(bip32_zero_key)));
    if (!bip32_priv_to_pub(&key_priv, &key_pub)) {
      LOGE("RAAK: pub fail");
      rsp->status = fwpb_status_KEY_DERIVATION_FAILED;
      goto out;
    }

    // Compressed public key: 1-byte prefix + 32-byte x-coordinate
    raak_rsp->hw_auth_public_key.bytes[0] = key_pub.prefix;
    memcpy(&raak_rsp->hw_auth_public_key.bytes[1], key_pub.key, BIP32_KEY_SIZE);
    raak_rsp->hw_auth_public_key.size = 1 + BIP32_KEY_SIZE;  // 33 bytes
  }

  rsp->status = fwpb_status_SUCCESS;

out:
  if (rsp->status == fwpb_status_SUCCESS) {
    ui_show_confirmation("Success", false);
  } else {
    LOGE("RAAK confirm: status %d", rsp->status);
  }
  raak_session_clear();
  confirmation_manager_clear();
  proto_send_rsp(cmd, rsp);
  return (rsp->status == fwpb_status_SUCCESS);
}

// ---------------------------------------------------------------------------
// Initial command handler: rotate_app_auth_keys_cmd
// ---------------------------------------------------------------------------
void rotate_app_auth_keys_handle_init(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->status = fwpb_status_ERROR;

  // Clear prior sessions
  confirmation_manager_clear();
  raak_session_clear();

  // Stash command data in session state
  memcpy(&raak_session.cmd_data, &cmd->msg.rotate_app_auth_keys_cmd,
         sizeof(fwpb_rotate_app_auth_keys_cmd));
  raak_session.valid = true;

  // Create confirmation with handles
  uint8_t response_handle[CONFIRMATION_HANDLE_SIZE];
  uint8_t confirmation_handle[CONFIRMATION_HANDLE_SIZE];
  uint8_t token = 1;

  confirmation_result_t res = confirmation_manager_create(
    CONFIRMATION_TYPE_ROTATE_APP_AUTH_KEYS, &token, sizeof(token), response_handle,
    sizeof(response_handle), confirmation_handle, sizeof(confirmation_handle));

  if (res != CONFIRMATION_RESULT_SUCCESS) {
    LOGE("RAAK CM create failed");
    raak_session_clear();
    goto out;
  }

  // Show confirmation prompt on display
  fwpb_display_params_privileged_action display_params = {0};
  display_params.sap_action = SAP_ACTION_ROTATE_APP_AUTH_KEYS;
  display_params.which_action = fwpb_display_params_privileged_action_confirm_action_tag;
  display_params.action.confirm_action.action_type =
    fwpb_display_privileged_action_type_DISPLAY_PRIVILEGED_ACTION_NONE;
  UI_SHOW_EVENT_WITH_DATA(UI_EVENT_START_PRIVILEGED_ACTION, &display_params,
                          sizeof(display_params));

  // Return CONFIRMATION_PENDING with handles
  rsp->status = fwpb_status_CONFIRMATION_PENDING;
  memcpy(rsp->response_handle.bytes, response_handle, sizeof(response_handle));
  rsp->response_handle.size = sizeof(response_handle);
  memcpy(rsp->confirmation_handle.bytes, confirmation_handle, sizeof(confirmation_handle));
  rsp->confirmation_handle.size = sizeof(confirmation_handle);

out:
  proto_send_rsp(cmd, rsp);
}

// ---------------------------------------------------------------------------
// Registration and session management
// ---------------------------------------------------------------------------
void rotate_app_auth_keys_register_handlers(void) {
  confirmation_manager_register_result_handler(CONFIRMATION_TYPE_ROTATE_APP_AUTH_KEYS,
                                               raak_confirmation_result_handler);
}

void rotate_app_auth_keys_clear_session(void) {
  raak_session_clear();
}
