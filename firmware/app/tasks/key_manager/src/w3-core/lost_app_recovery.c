#include "aes.h"
#include "confirmation_manager.h"
#include "display.pb.h"
#include "ecc.h"
#include "hash.h"
#include "ipc.h"
#include "log.h"
#include "lost_app_recovery_impl.h"
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

// Forward declaration
static void lost_app_recovery_sign_challenge_register_handlers(void);

// Session state for lost app recovery (persists between commands within same NFC session).
// Validation/handles/UI for the initial command live in [unseal] (sealed_unseal_session_t);
// [confirmed] is set in the result handler so the continue command can proceed.
typedef struct {
  sealed_unseal_session_t unseal;
  // Flag: confirmation completed, continue command expected
  bool confirmed;
} lost_app_recovery_session_t;

static SHARED_TASK_BSS lost_app_recovery_session_t lar_session = {0};

static void lar_session_clear(void) {
  memzero(&lar_session, sizeof(lar_session));
}

// ---------------------------------------------------------------------------
// Initial command handler: lost_app_recovery_cmd
// ---------------------------------------------------------------------------
void lost_app_recovery_handle_init(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  if (!sealed_unseal_begin(&cmd->msg.lost_app_recovery_cmd.sealed_ssek,
                           SAP_ACTION_CREATE_LOST_APP_RECOVERY, CONFIRMATION_TYPE_LOST_APP_RECOVERY,
                           &lar_session.unseal, rsp)) {
    LOGE("LAR: begin failed, status=%d", rsp->status);
  }
  // sealed_unseal_begin clears the session it owns; clear our extra state too.
  lar_session.confirmed = false;
  proto_send_rsp(cmd, rsp);
}

// ---------------------------------------------------------------------------
// Confirmation result handler (called when get_confirmation_result_cmd arrives)
// ---------------------------------------------------------------------------
static bool lar_confirmation_result_handler(ipc_ref_t* message) {
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
    LOGE("LAR confirm fail: %d", validation);
    lar_session_clear();
    confirmation_manager_clear();
    proto_send_rsp(cmd, rsp);
    return false;
  }

  if (!lar_session.unseal.valid) {
    rsp->status = fwpb_status_ERROR;
    LOGE("LAR no session");
    confirmation_manager_clear();
    proto_send_rsp(cmd, rsp);
    return false;
  }

  // Unseal SSEK using hardware sealing key
  uint8_t unsealed_ssek[AES_256_LENGTH_BYTES] = {0};
  if (!sealed_data_unseal(&lar_session.unseal.sealed_key, unsealed_ssek, sizeof(unsealed_ssek))) {
    LOGE("LAR unseal fail");
    rsp->status = fwpb_status_ERROR;
    lar_session_clear();
    proto_send_rsp(cmd, rsp);
    return false;
  }

  // Return unsealed SSEK via get_confirmation_result_rsp
  rsp->msg.get_confirmation_result_rsp.which_result =
    fwpb_get_confirmation_result_rsp_lost_app_recovery_ssek_rsp_tag;
  memcpy(rsp->msg.get_confirmation_result_rsp.result.lost_app_recovery_ssek_rsp.unsealed_ssek.bytes,
         unsealed_ssek, sizeof(unsealed_ssek));
  rsp->msg.get_confirmation_result_rsp.result.lost_app_recovery_ssek_rsp.unsealed_ssek.size =
    sizeof(unsealed_ssek);
  rsp->status = fwpb_status_SUCCESS;

  // Zero sensitive data
  memzero(unsealed_ssek, sizeof(unsealed_ssek));

  // Keep session alive -- expect lost_app_recovery_continue_cmd next
  lar_session.confirmed = true;

  // DO NOT clear confirmation_manager -- we need session to stay alive.
  // The continue handler will clear everything.

  proto_send_rsp(cmd, rsp);
  return true;
}

// ---------------------------------------------------------------------------
// Registration and session accessors
// ---------------------------------------------------------------------------

bool lost_app_recovery_is_session_ready(void) {
  return lar_session.unseal.valid && lar_session.confirmed;
}

void lost_app_recovery_register_handlers(void) {
  confirmation_manager_register_result_handler(CONFIRMATION_TYPE_LOST_APP_RECOVERY,
                                               lar_confirmation_result_handler);
  lost_app_recovery_sign_challenge_register_handlers();
}

void lost_app_recovery_clear_session(void) {
  lar_session_clear();
}

// ===========================================================================
// Lost App Recovery Sign Challenge (confirmable auth challenge signing)
// ===========================================================================

typedef struct {
  uint8_t challenge_hash[SHA256_DIGEST_SIZE];
  bool valid;
  bool signed_ok;
  bool sign_attempted;
  fwpb_status sign_result;
  uint8_t signature[ECC_SIG_SIZE];
} lar_sign_challenge_session_t;

static SHARED_TASK_BSS lar_sign_challenge_session_t lar_sc_session = {0};

static void lar_sc_session_clear(void) {
  memzero(&lar_sc_session, sizeof(lar_sc_session));
}

static void lar_sc_clear_state(void) {
  confirmation_manager_clear();
  lar_sc_session_clear();
}

// Deferred signing: sign the challenge hash after user confirms on device
static void lar_sc_sign(void) {
  lar_sc_session.sign_attempted = true;

  extended_key_t auth_key __attribute__((__cleanup__(bip32_zero_key)));
  if (!wallet_get_w1_auth_key(&auth_key)) {
    LOGE("LAR-SC auth key unavailable");
    lar_sc_session.sign_result = fwpb_status_KEY_DERIVATION_FAILED;
    return;
  }

  uint8_t sig[ECC_SIG_SIZE] = {0};
  if (!bip32_sign(&auth_key, lar_sc_session.challenge_hash, sig)) {
    LOGE("LAR-SC signing failed");
    lar_sc_session.sign_result = fwpb_status_SIGNING_FAILED;
    return;
  }

  memcpy(lar_sc_session.signature, sig, ECC_SIG_SIZE);
  memzero(sig, sizeof(sig));
  lar_sc_session.signed_ok = true;
  lar_sc_session.sign_result = fwpb_status_SUCCESS;
}

// ---------------------------------------------------------------------------
// Initial command handler: lost_app_recovery_sign_challenge_cmd
// ---------------------------------------------------------------------------
void lost_app_recovery_sign_challenge_handle_init(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_lost_app_recovery_sign_challenge_rsp_tag;
  rsp->status = fwpb_status_ERROR;

  // Clear prior sessions
  lar_sc_clear_state();

  // Validate challenge
  if (cmd->msg.lost_app_recovery_sign_challenge_cmd.challenge.size == 0) {
    LOGE("LAR-SC: empty challenge");
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }

  // Early check: verify auth key is available before showing UI
  extended_key_t auth_key __attribute__((__cleanup__(bip32_zero_key)));
  if (!wallet_get_w1_auth_key(&auth_key)) {
    LOGE("LAR-SC auth key unavailable");
    rsp->status = fwpb_status_KEY_DERIVATION_FAILED;
    goto out;
  }

  // Hash the challenge with domain separation: SHA-256("BKAuthChallenge" || challenge_bytes)
  {
#define LAR_SC_DOMAIN_TAG "BKAuthChallenge"
    hash_stream_ctx_t ctx;
    if (!crypto_sha256_stream_init(&ctx) ||
        !crypto_sha256_stream_update(&ctx, (uint8_t*)LAR_SC_DOMAIN_TAG,
                                     sizeof(LAR_SC_DOMAIN_TAG) - 1) ||
        !crypto_sha256_stream_update(
          &ctx, (uint8_t*)cmd->msg.lost_app_recovery_sign_challenge_cmd.challenge.bytes,
          cmd->msg.lost_app_recovery_sign_challenge_cmd.challenge.size) ||
        !crypto_sha256_stream_final(&ctx, lar_sc_session.challenge_hash)) {
      LOGE("LAR-SC hash failed");
      goto out;
    }
#undef LAR_SC_DOMAIN_TAG
  }
  lar_sc_session.valid = true;

  // Create confirmation handles
  uint8_t response_handle[CONFIRMATION_HANDLE_SIZE];
  uint8_t confirmation_handle[CONFIRMATION_HANDLE_SIZE];
  uint8_t token = 1;  // Placeholder operation data

  confirmation_result_t res = confirmation_manager_create(
    CONFIRMATION_TYPE_LOST_APP_RECOVERY_SIGN_CHALLENGE, &token, sizeof(token), response_handle,
    sizeof(response_handle), confirmation_handle, sizeof(confirmation_handle));

  if (res != CONFIRMATION_RESULT_SUCCESS) {
    LOGE("LAR-SC CM create failed");
    lar_sc_session_clear();
    goto out;
  }

  // Show confirmation prompt on display (title resolved via SAP action -> langpack lookup).
  fwpb_display_params_privileged_action display_params = {0};
  display_params.sap_action = SAP_ACTION_APPROVE_APP_RECOVERY;
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
// Confirmation result handler for sign challenge
// ---------------------------------------------------------------------------
static bool lar_sc_confirmation_result_handler(ipc_ref_t* message) {
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
    LOGE("LAR-SC confirm fail: %d", validation);
    lar_sc_clear_state();
    proto_send_rsp(cmd, rsp);
    return false;
  }

  // Sign the challenge if not yet done
  if (!lar_sc_session.sign_attempted) {
    lar_sc_sign();
  }

  if (lar_sc_session.signed_ok) {
    rsp->msg.get_confirmation_result_rsp.which_result =
      fwpb_get_confirmation_result_rsp_lost_app_recovery_sign_challenge_result_tag;
    memcpy(rsp->msg.get_confirmation_result_rsp.result.lost_app_recovery_sign_challenge_result
             .signature.bytes,
           lar_sc_session.signature, ECC_SIG_SIZE);
    rsp->msg.get_confirmation_result_rsp.result.lost_app_recovery_sign_challenge_result.signature
      .size = ECC_SIG_SIZE;
    rsp->status = fwpb_status_SUCCESS;
    lar_sc_clear_state();
    ui_show_confirmation("Success", false);
    proto_send_rsp(cmd, rsp);
    return true;
  }

  if (lar_sc_session.sign_attempted) {
    rsp->status = (fwpb_status)lar_sc_session.sign_result;
    lar_sc_clear_state();
    proto_send_rsp(cmd, rsp);
    return false;
  }

  rsp->status = fwpb_status_CONFIRMATION_PENDING;
  proto_send_rsp(cmd, rsp);
  return true;
}

// ---------------------------------------------------------------------------
// Registration for sign challenge confirmation handler
// ---------------------------------------------------------------------------
static void lost_app_recovery_sign_challenge_register_handlers(void) {
  confirmation_manager_register_result_handler(CONFIRMATION_TYPE_LOST_APP_RECOVERY_SIGN_CHALLENGE,
                                               lar_sc_confirmation_result_handler);
}
