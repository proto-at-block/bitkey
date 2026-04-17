#include "bip32.h"
#include "confirmation_manager.h"
#include "display.pb.h"
#include "ecc.h"
#include "hash.h"
#include "ipc.h"
#include "key_manager_task_impl.h"
#include "log.h"
#include "proto_helpers.h"
#include "secutils.h"
#include "seed.h"
#include "sign_action_proof_core.h"
#include "ui_events.h"
#include "ui_messaging.h"
#include "upgrade_rotate_app_auth_keys_impl.h"
#include "wallet.h"
#include "wallet.pb.h"
#include "wstring.h"

#include <string.h>

// Session state for upgrade rotate app auth keys (persists between init and confirmation)
typedef struct {
  fwpb_upgrade_rotate_app_auth_keys_cmd cmd_data;
  bool valid;
} upgrade_raak_session_t;

static SHARED_TASK_BSS upgrade_raak_session_t upgrade_raak_session = {0};

static void upgrade_raak_session_clear(void) {
  memzero(&upgrade_raak_session, sizeof(upgrade_raak_session));
}

// ---------------------------------------------------------------------------
// Confirmation result handler
// ---------------------------------------------------------------------------
static bool upgrade_raak_confirmation_result_handler(ipc_ref_t* message) {
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
    LOGE("UpgradeRAAK confirm fail: %d", validation);
    upgrade_raak_session_clear();
    confirmation_manager_clear();
    proto_send_rsp(cmd, rsp);
    return false;
  }

  if (!upgrade_raak_session.valid) {
    rsp->status = fwpb_status_ERROR;
    LOGE("UpgradeRAAK no session");
    confirmation_manager_clear();
    proto_send_rsp(cmd, rsp);
    return false;
  }

  rsp->msg.get_confirmation_result_rsp.which_result =
    fwpb_get_confirmation_result_rsp_upgrade_rotate_app_auth_keys_rsp_tag;
  fwpb_upgrade_rotate_app_auth_keys_rsp* uraak_rsp =
    &rsp->msg.get_confirmation_result_rsp.result.upgrade_rotate_app_auth_keys_rsp;

  derivation_path_t* auth_path = wallet_get_w1_auth_path();

  // 1. Sign account_id with domain separation: SHA-256("BKAuthRotation" || account_id)
  {
    uint8_t hash[SHA256_DIGEST_SIZE] = {0};
    size_t account_id_len = strlen(upgrade_raak_session.cmd_data.account_id);
    if (account_id_len == 0) {
      rsp->status = fwpb_status_INVALID_ARGUMENT;
      goto out;
    }
#define URAAK_ACCT_DOMAIN_TAG "BKAuthRotation"
    hash_stream_ctx_t ctx;
    if (!crypto_sha256_stream_init(&ctx) ||
        !crypto_sha256_stream_update(&ctx, (uint8_t*)URAAK_ACCT_DOMAIN_TAG,
                                     sizeof(URAAK_ACCT_DOMAIN_TAG) - 1) ||
        !crypto_sha256_stream_update(&ctx, (uint8_t*)upgrade_raak_session.cmd_data.account_id,
                                     (uint32_t)account_id_len) ||
        !crypto_sha256_stream_final(&ctx, hash)) {
      rsp->status = fwpb_status_ERROR;
      goto out;
    }
#undef URAAK_ACCT_DOMAIN_TAG
    key_manager_sign_result_t sign_res =
      key_manager_derive_and_sign(*auth_path, hash, uraak_rsp->hw_signed_account_id.bytes);
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
    uraak_rsp->hw_signed_account_id.size = ECC_SIG_SIZE;
  }

  // 2. Sign app_global_auth_key with domain separation:
  //    SHA-256("BKRelationshipEndorsement" || hex_pubkey_string)
  {
    uint8_t hash[SHA256_DIGEST_SIZE] = {0};
    size_t app_key_len = strlen(upgrade_raak_session.cmd_data.app_global_auth_key);
    if (app_key_len == 0) {
      rsp->status = fwpb_status_INVALID_ARGUMENT;
      goto out;
    }
#define URAAK_KEY_DOMAIN_TAG "BKRelationshipEndorsement"
    hash_stream_ctx_t ctx;
    if (!crypto_sha256_stream_init(&ctx) ||
        !crypto_sha256_stream_update(&ctx, (uint8_t*)URAAK_KEY_DOMAIN_TAG,
                                     sizeof(URAAK_KEY_DOMAIN_TAG) - 1) ||
        !crypto_sha256_stream_update(&ctx,
                                     (uint8_t*)upgrade_raak_session.cmd_data.app_global_auth_key,
                                     (uint32_t)app_key_len) ||
        !crypto_sha256_stream_final(&ctx, hash)) {
      rsp->status = fwpb_status_ERROR;
      goto out;
    }
#undef URAAK_KEY_DOMAIN_TAG
    key_manager_sign_result_t sign_res =
      key_manager_derive_and_sign(*auth_path, hash, uraak_rsp->app_auth_key_signature.bytes);
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
    uraak_rsp->app_auth_key_signature.size = ECC_SIG_SIZE;
  }

  // 3. Derive and return HW auth public key (33-byte compressed)
  {
    extended_key_t key_priv __attribute__((__cleanup__(bip32_zero_key)));
    fingerprint_t master_fp;
    fingerprint_t parent_fp;

    if (seed_derive_bip32(*auth_path, &key_priv, &master_fp, &parent_fp) != SEED_RES_OK) {
      LOGE("UpgradeRAAK: key deriv fail");
      rsp->status = fwpb_status_KEY_DERIVATION_FAILED;
      goto out;
    }

    extended_key_t key_pub __attribute__((__cleanup__(bip32_zero_key)));
    if (!bip32_priv_to_pub(&key_priv, &key_pub)) {
      LOGE("UpgradeRAAK: pub fail");
      rsp->status = fwpb_status_KEY_DERIVATION_FAILED;
      goto out;
    }

    // Compressed public key: 1-byte prefix + 32-byte x-coordinate
    uraak_rsp->hw_auth_public_key.bytes[0] = key_pub.prefix;
    memcpy(&uraak_rsp->hw_auth_public_key.bytes[1], key_pub.key, BIP32_KEY_SIZE);
    uraak_rsp->hw_auth_public_key.size = 1 + BIP32_KEY_SIZE;  // 33 bytes
  }

  rsp->status = fwpb_status_SUCCESS;

out:
  if (rsp->status == fwpb_status_SUCCESS) {
    ui_show_confirmation("Success", false);
  } else {
    LOGE("UpgradeRAAK confirm: status %d", rsp->status);
  }
  upgrade_raak_session_clear();
  confirmation_manager_clear();
  proto_send_rsp(cmd, rsp);
  return (rsp->status == fwpb_status_SUCCESS);
}

// ---------------------------------------------------------------------------
// Initial command handler: upgrade_rotate_app_auth_keys_cmd
// ---------------------------------------------------------------------------
void upgrade_rotate_app_auth_keys_handle_init(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->status = fwpb_status_ERROR;

  // Clear prior sessions
  confirmation_manager_clear();
  upgrade_raak_session_clear();

  // Stash command data in session state
  memcpy(&upgrade_raak_session.cmd_data, &cmd->msg.upgrade_rotate_app_auth_keys_cmd,
         sizeof(fwpb_upgrade_rotate_app_auth_keys_cmd));
  upgrade_raak_session.valid = true;

  // Create confirmation with handles
  uint8_t response_handle[CONFIRMATION_HANDLE_SIZE];
  uint8_t confirmation_handle[CONFIRMATION_HANDLE_SIZE];
  uint8_t token = 1;

  confirmation_result_t res = confirmation_manager_create(
    CONFIRMATION_TYPE_UPGRADE_ROTATE_APP_AUTH_KEYS, &token, sizeof(token), response_handle,
    sizeof(response_handle), confirmation_handle, sizeof(confirmation_handle));

  if (res != CONFIRMATION_RESULT_SUCCESS) {
    LOGE("UpgradeRAAK CM create failed");
    upgrade_raak_session_clear();
    goto out;
  }

  // Show confirmation prompt on display
  fwpb_display_params_privileged_action display_params = {0};
  display_params.sap_action = SAP_ACTION_INITIATE_WALLET_UPGRADE;
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
void upgrade_rotate_app_auth_keys_register_handlers(void) {
  confirmation_manager_register_result_handler(CONFIRMATION_TYPE_UPGRADE_ROTATE_APP_AUTH_KEYS,
                                               upgrade_raak_confirmation_result_handler);
}

void upgrade_rotate_app_auth_keys_clear_session(void) {
  upgrade_raak_session_clear();
}
