#include "aes.h"
#include "bip32.h"
#include "confirmation_manager.h"
#include "display.pb.h"
#include "ecc.h"
#include "hash.h"
#include "ipc.h"
#include "key_manager_task_impl.h"
#include "log.h"
#include "proto_helpers.h"
#include "recovery_composites_impl.h"
#include "sealed_data_utils.h"
#include "secutils.h"
#include "sign_action_proof_core.h"
#include "ui_events.h"
#include "ui_messaging.h"
#include "wallet.h"
#include "wallet.pb.h"
#include "wstring.h"

#include <string.h>

// ===========================================================================
// Helper: seal raw bytes with hardware sealing key (AES-256-GCM)
// ===========================================================================
static bool seal_bytes(const uint8_t* input, size_t input_size, fwpb_sealed_data* sealed_out) {
  if (input_size == 0 || input_size > sizeof(sealed_out->data.bytes)) {
    return false;
  }
  uint8_t nonce[AES_GCM_IV_LENGTH] = {0};
  uint8_t tag[AES_GCM_TAG_LENGTH] = {0};
  uint8_t ciphertext[AES_256_LENGTH_BYTES] = {0};

  wallet_res_t res = wallet_csek_encrypt((uint8_t*)input, ciphertext, input_size, nonce, tag);
  if (res != WALLET_RES_OK) {
    return false;
  }

  memcpy(sealed_out->data.bytes, ciphertext, input_size);
  sealed_out->data.size = input_size;
  memcpy(sealed_out->nonce.bytes, nonce, AES_GCM_IV_LENGTH);
  sealed_out->nonce.size = AES_GCM_IV_LENGTH;
  memcpy(sealed_out->tag.bytes, tag, AES_GCM_TAG_LENGTH);
  sealed_out->tag.size = AES_GCM_TAG_LENGTH;

  memzero(ciphertext, sizeof(ciphertext));
  return true;
}

// ===========================================================================
// Helper: build and return CONFIRMATION_PENDING with handles
// ===========================================================================
static bool create_confirmation_with_prompt(fwpb_wallet_cmd* UNUSED(cmd), fwpb_wallet_rsp* rsp,
                                            confirmation_type_t type, sap_action_t sap_action) {
  uint8_t response_handle[CONFIRMATION_HANDLE_SIZE];
  uint8_t confirmation_handle[CONFIRMATION_HANDLE_SIZE];
  uint8_t token = 1;

  confirmation_result_t res = confirmation_manager_create(
    type, &token, sizeof(token), response_handle, sizeof(response_handle), confirmation_handle,
    sizeof(confirmation_handle));

  if (res != CONFIRMATION_RESULT_SUCCESS) {
    LOGE("RC CM create failed");
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

// ===========================================================================
// Helper: validate confirmation handles and return status
// Returns: 1 = approved, 0 = still pending, -1 = error
// ===========================================================================
static int validate_confirmation(ipc_ref_t* message, fwpb_wallet_cmd** cmd_out,
                                 fwpb_wallet_rsp** rsp_out) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  *cmd_out = cmd;
  *rsp_out = rsp;

  rsp->which_msg = fwpb_wallet_rsp_get_confirmation_result_rsp_tag;

  confirmation_result_t validation =
    confirmation_manager_validate(cmd->msg.get_confirmation_result_cmd.response_handle.bytes,
                                  cmd->msg.get_confirmation_result_cmd.response_handle.size,
                                  cmd->msg.get_confirmation_result_cmd.confirmation_handle.bytes,
                                  cmd->msg.get_confirmation_result_cmd.confirmation_handle.size);

  if (validation == CONFIRMATION_RESULT_NOT_APPROVED) {
    rsp->status = fwpb_status_CONFIRMATION_PENDING;
    proto_send_rsp(cmd, rsp);
    return 0;
  }

  if (validation != CONFIRMATION_RESULT_SUCCESS) {
    rsp->status = fwpb_status_CONFIRMATION_NOT_COMPLETED;
    LOGE("RC confirm fail: %d", validation);
    proto_send_rsp(cmd, rsp);
    return -1;
  }

  return 1;
}

// ===========================================================================
// Helper: sign a SAP proof with given action and bindings
// ===========================================================================
static bool sign_sap_proof(const char* action_str, const char* bindings, uint32_t version,
                           uint8_t sig_out[ECC_SIG_SIZE]) {
  sap_session_t sap = {0};
  sap_session_init(&sap);
  sap.pending_data.valid = true;
  sap.pending_data.version = version;

  if (strlen(action_str) >= sizeof(sap.pending_data.action) ||
      strlen(bindings) >= sizeof(sap.pending_data.bindings)) {
    return false;
  }

  strncpy(sap.pending_data.action, action_str, sizeof(sap.pending_data.action) - 1);
  // value is empty for these proofs
  sap.pending_data.value[0] = '\0';
  strncpy(sap.pending_data.bindings, bindings, sizeof(sap.pending_data.bindings) - 1);

  int status = sap_sign(&sap);
  if (status != fwpb_status_SUCCESS) {
    return false;
  }

  memcpy(sig_out, sap.signature, ECC_SIG_SIZE);
  return true;
}

// ===========================================================================
// 1. Sign Challenge and Seal SEKS
// ===========================================================================

typedef struct {
  uint8_t challenge_hash[SHA256_DIGEST_SIZE];
  uint8_t unsealed_csek[AES_256_LENGTH_BYTES];
  uint8_t unsealed_ssek[AES_256_LENGTH_BYTES];
  bool valid;
} scas_session_t;

static SHARED_TASK_BSS scas_session_t scas_session = {0};

static void scas_clear(void) {
  memzero(&scas_session, sizeof(scas_session));
}

static void scas_clear_all(void) {
  confirmation_manager_clear();
  scas_clear();
}

void recovery_composites_sign_challenge_and_seal_handle_init(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->status = fwpb_status_ERROR;

  scas_clear_all();

  fwpb_sign_challenge_and_seal_seks_cmd* c = &cmd->msg.sign_challenge_and_seal_seks_cmd;

  // Validate inputs
  if (c->challenge.size == 0) {
    LOGE("SCAS: empty challenge");
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }
  if (c->unsealed_csek.size != AES_256_LENGTH_BYTES) {
    LOGE("SCAS: bad CSEK size");
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }
  if (c->unsealed_ssek.size != AES_256_LENGTH_BYTES) {
    LOGE("SCAS: bad SSEK size");
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }

  // Early check: verify auth key is available
  {
    extended_key_t auth_key __attribute__((__cleanup__(bip32_zero_key)));
    if (!wallet_get_w1_auth_key(&auth_key)) {
      LOGE("SCAS: auth key unavailable");
      rsp->status = fwpb_status_KEY_DERIVATION_FAILED;
      goto out;
    }
  }

  // Hash challenge with domain separation: SHA-256("BKDelayNotifyChallenge" || challenge_bytes)
  {
#define SCAS_DOMAIN_TAG "BKDelayNotifyChallenge"
    hash_stream_ctx_t ctx;
    if (!crypto_sha256_stream_init(&ctx) ||
        !crypto_sha256_stream_update(&ctx, (uint8_t*)SCAS_DOMAIN_TAG,
                                     sizeof(SCAS_DOMAIN_TAG) - 1) ||
        !crypto_sha256_stream_update(&ctx, (uint8_t*)c->challenge.bytes, c->challenge.size) ||
        !crypto_sha256_stream_final(&ctx, scas_session.challenge_hash)) {
      LOGE("SCAS: hash failed");
      goto out;
    }
#undef SCAS_DOMAIN_TAG
  }

  // Stash unsealed keys
  memcpy(scas_session.unsealed_csek, c->unsealed_csek.bytes, AES_256_LENGTH_BYTES);
  memcpy(scas_session.unsealed_ssek, c->unsealed_ssek.bytes, AES_256_LENGTH_BYTES);
  scas_session.valid = true;

  if (!create_confirmation_with_prompt(cmd, rsp, CONFIRMATION_TYPE_SIGN_CHALLENGE_AND_SEAL_SEKS,
                                       SAP_ACTION_START_RECOVERY)) {
    scas_clear();
    goto out;
  }

out:
  proto_send_rsp(cmd, rsp);
}

static bool scas_confirmation_result_handler(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd;
  fwpb_wallet_rsp* rsp;

  int result = validate_confirmation(message, &cmd, &rsp);
  if (result <= 0) {
    if (result < 0)
      scas_clear_all();
    return (result == 0);
  }

  if (!scas_session.valid) {
    rsp->status = fwpb_status_ERROR;
    LOGE("SCAS: no session");
    scas_clear_all();
    proto_send_rsp(cmd, rsp);
    return false;
  }

  // Sign challenge with HW auth key
  extended_key_t auth_key __attribute__((__cleanup__(bip32_zero_key)));
  if (!wallet_get_w1_auth_key(&auth_key)) {
    LOGE("SCAS: auth key fail");
    rsp->status = fwpb_status_KEY_DERIVATION_FAILED;
    goto fail;
  }

  {
    fwpb_sign_challenge_and_seal_seks_rsp* out =
      &rsp->msg.get_confirmation_result_rsp.result.sign_challenge_and_seal_seks_result;

    uint8_t sig[ECC_SIG_SIZE] = {0};
    if (!bip32_sign(&auth_key, scas_session.challenge_hash, sig)) {
      LOGE("SCAS: sign fail");
      rsp->status = fwpb_status_SIGNING_FAILED;
      goto fail;
    }
    memcpy(out->signature.bytes, sig, ECC_SIG_SIZE);
    out->signature.size = ECC_SIG_SIZE;
    memzero(sig, sizeof(sig));

    // Seal CSEK
    if (!seal_bytes(scas_session.unsealed_csek, AES_256_LENGTH_BYTES, &out->sealed_csek)) {
      LOGE("SCAS: seal CSEK fail");
      goto fail;
    }
    out->has_sealed_csek = true;

    // Seal SSEK
    if (!seal_bytes(scas_session.unsealed_ssek, AES_256_LENGTH_BYTES, &out->sealed_ssek)) {
      LOGE("SCAS: seal SSEK fail");
      goto fail;
    }
    out->has_sealed_ssek = true;
  }

  rsp->msg.get_confirmation_result_rsp.which_result =
    fwpb_get_confirmation_result_rsp_sign_challenge_and_seal_seks_result_tag;
  rsp->status = fwpb_status_SUCCESS;
  scas_clear_all();
  ui_show_confirmation("Success", false);
  proto_send_rsp(cmd, rsp);
  return true;

fail:
  if (rsp->status != fwpb_status_ERROR && rsp->status != fwpb_status_SIGNING_FAILED &&
      rsp->status != fwpb_status_KEY_DERIVATION_FAILED &&
      rsp->status != fwpb_status_INVALID_ARGUMENT) {
    rsp->status = fwpb_status_ERROR;
  }
  scas_clear_all();
  proto_send_rsp(cmd, rsp);
  return false;
}

// ===========================================================================
// 2. Recovery Authorize Lost App
// ===========================================================================

typedef struct {
  fwpb_sealed_data sealed_ddk;
  bool has_sealed_ddk;
  fwpb_sealed_data sealed_ssek;
  bool has_sealed_ssek;
  char descriptor_backups_bindings[256];
  char activate_keyset_bindings[256];
  uint32_t action_proof_version;
  bool valid;
} rala_session_t;

static SHARED_TASK_BSS rala_session_t rala_session = {0};

static void rala_clear(void) {
  memzero(&rala_session, sizeof(rala_session));
}

static void rala_clear_all(void) {
  confirmation_manager_clear();
  rala_clear();
}

void recovery_composites_authorize_lost_app_handle_init(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->status = fwpb_status_ERROR;

  rala_clear_all();

  fwpb_recovery_authorize_lost_app_cmd* c = &cmd->msg.recovery_authorize_lost_app_cmd;

  // Stash sealed data (optional fields)
  if (c->sealed_ddk.data.size > 0) {
    memcpy(&rala_session.sealed_ddk, &c->sealed_ddk, sizeof(fwpb_sealed_data));
    rala_session.has_sealed_ddk = true;
  }
  if (c->sealed_ssek.data.size > 0) {
    memcpy(&rala_session.sealed_ssek, &c->sealed_ssek, sizeof(fwpb_sealed_data));
    rala_session.has_sealed_ssek = true;
  }

  // Stash bindings (reject if too long to avoid silent truncation)
  if (strnlen(c->descriptor_backups_bindings, sizeof(rala_session.descriptor_backups_bindings)) >=
      sizeof(rala_session.descriptor_backups_bindings)) {
    LOGE("RALA: desc OOB");
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }
  if (strnlen(c->activate_keyset_bindings, sizeof(rala_session.activate_keyset_bindings)) >=
      sizeof(rala_session.activate_keyset_bindings)) {
    LOGE("RALA: ks OOB");
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }
  strncpy(rala_session.descriptor_backups_bindings, c->descriptor_backups_bindings,
          sizeof(rala_session.descriptor_backups_bindings) - 1);
  strncpy(rala_session.activate_keyset_bindings, c->activate_keyset_bindings,
          sizeof(rala_session.activate_keyset_bindings) - 1);
  rala_session.action_proof_version = c->action_proof_version;
  rala_session.valid = true;

  if (!create_confirmation_with_prompt(cmd, rsp, CONFIRMATION_TYPE_RECOVERY_AUTHORIZE_LOST_APP,
                                       SAP_ACTION_APPROVE_APP_RECOVERY)) {
    rala_clear();
    goto out;
  }

out:
  proto_send_rsp(cmd, rsp);
}

static bool rala_confirmation_result_handler(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd;
  fwpb_wallet_rsp* rsp;

  int result = validate_confirmation(message, &cmd, &rsp);
  if (result <= 0) {
    if (result < 0)
      rala_clear_all();
    return (result == 0);
  }

  if (!rala_session.valid) {
    rsp->status = fwpb_status_ERROR;
    LOGE("RALA: no session");
    rala_clear_all();
    proto_send_rsp(cmd, rsp);
    return false;
  }

  fwpb_recovery_authorize_lost_app_rsp* out =
    &rsp->msg.get_confirmation_result_rsp.result.recovery_authorize_lost_app_result;

  // 1. Unseal DDK if present
  if (rala_session.has_sealed_ddk) {
    uint8_t unsealed[AES_256_LENGTH_BYTES] = {0};
    if (!sealed_data_unseal(&rala_session.sealed_ddk, unsealed, sizeof(unsealed))) {
      LOGE("RALA: unseal DDK fail");
      goto fail;
    }
    memcpy(out->unsealed_ddk_data.bytes, unsealed, sizeof(unsealed));
    out->unsealed_ddk_data.size = sizeof(unsealed);
    memzero(unsealed, sizeof(unsealed));
  }

  // 2. Unseal SSEK if present
  if (rala_session.has_sealed_ssek) {
    uint8_t unsealed[AES_256_LENGTH_BYTES] = {0};
    if (!sealed_data_unseal(&rala_session.sealed_ssek, unsealed, sizeof(unsealed))) {
      LOGE("RALA: unseal SSEK fail");
      goto fail;
    }
    memcpy(out->unsealed_ssek.bytes, unsealed, sizeof(unsealed));
    out->unsealed_ssek.size = sizeof(unsealed);
    memzero(unsealed, sizeof(unsealed));
  }

  // 3. Sign UpdateDescriptorBackups SAP proof
  if (!sign_sap_proof("UpdateDescriptorBackups", rala_session.descriptor_backups_bindings,
                      rala_session.action_proof_version, out->descriptor_backups_signature.bytes)) {
    LOGE("RALA: SAP desc backups fail");
    rsp->status = fwpb_status_SIGNING_FAILED;
    goto fail;
  }
  out->descriptor_backups_signature.size = ECC_SIG_SIZE;

  // 4. Sign RotateSpendingKeyset SAP proof
  if (!sign_sap_proof("RotateSpendingKeyset", rala_session.activate_keyset_bindings,
                      rala_session.action_proof_version, out->activate_keyset_signature.bytes)) {
    LOGE("RALA: SAP ks fail");
    rsp->status = fwpb_status_SIGNING_FAILED;
    goto fail;
  }
  out->activate_keyset_signature.size = ECC_SIG_SIZE;

  rsp->msg.get_confirmation_result_rsp.which_result =
    fwpb_get_confirmation_result_rsp_recovery_authorize_lost_app_result_tag;
  rsp->status = fwpb_status_SUCCESS;
  rala_clear_all();
  ui_show_confirmation("Success", false);  // Don't lock -- verifyKeysAndBuildDescriptor follows
  proto_send_rsp(cmd, rsp);
  return true;

fail:
  if (rsp->status != fwpb_status_ERROR && rsp->status != fwpb_status_SIGNING_FAILED &&
      rsp->status != fwpb_status_KEY_DERIVATION_FAILED &&
      rsp->status != fwpb_status_INVALID_ARGUMENT) {
    rsp->status = fwpb_status_ERROR;
  }
  rala_clear_all();
  proto_send_rsp(cmd, rsp);
  return false;
}

// ===========================================================================
// 3. Recovery Authorize Lost HW
// ===========================================================================

typedef struct {
  uint8_t ddk_private_key[AES_256_LENGTH_BYTES];
  bool has_ddk;
  char descriptor_backups_bindings[256];
  char activate_keyset_bindings[256];
  uint32_t action_proof_version;
  bool valid;
} ralh_session_t;

static SHARED_TASK_BSS ralh_session_t ralh_session = {0};

static void ralh_clear(void) {
  memzero(&ralh_session, sizeof(ralh_session));
}

static void ralh_clear_all(void) {
  confirmation_manager_clear();
  ralh_clear();
}

void recovery_composites_authorize_lost_hw_handle_init(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->status = fwpb_status_ERROR;

  ralh_clear_all();

  fwpb_recovery_authorize_lost_hw_cmd* c = &cmd->msg.recovery_authorize_lost_hw_cmd;

  // Stash DDK private key (optional)
  if (c->ddk_private_key.size > 0) {
    if (c->ddk_private_key.size != AES_256_LENGTH_BYTES) {
      LOGE("RALH: bad DDK size");
      rsp->status = fwpb_status_INVALID_ARGUMENT;
      goto out;
    }
    memcpy(ralh_session.ddk_private_key, c->ddk_private_key.bytes, AES_256_LENGTH_BYTES);
    ralh_session.has_ddk = true;
  }

  // Stash bindings (reject if too long to avoid silent truncation)
  if (strnlen(c->descriptor_backups_bindings, sizeof(ralh_session.descriptor_backups_bindings)) >=
      sizeof(ralh_session.descriptor_backups_bindings)) {
    LOGE("RALH: desc OOB");
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }
  if (strnlen(c->activate_keyset_bindings, sizeof(ralh_session.activate_keyset_bindings)) >=
      sizeof(ralh_session.activate_keyset_bindings)) {
    LOGE("RALH: ks OOB");
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }
  strncpy(ralh_session.descriptor_backups_bindings, c->descriptor_backups_bindings,
          sizeof(ralh_session.descriptor_backups_bindings) - 1);
  strncpy(ralh_session.activate_keyset_bindings, c->activate_keyset_bindings,
          sizeof(ralh_session.activate_keyset_bindings) - 1);
  ralh_session.action_proof_version = c->action_proof_version;
  ralh_session.valid = true;

  if (!create_confirmation_with_prompt(cmd, rsp, CONFIRMATION_TYPE_RECOVERY_AUTHORIZE_LOST_HW,
                                       SAP_ACTION_COMPLETE_WALLET)) {
    ralh_clear();
    goto out;
  }

out:
  proto_send_rsp(cmd, rsp);
}

static bool ralh_confirmation_result_handler(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd;
  fwpb_wallet_rsp* rsp;

  int result = validate_confirmation(message, &cmd, &rsp);
  if (result <= 0) {
    if (result < 0)
      ralh_clear_all();
    return (result == 0);
  }

  if (!ralh_session.valid) {
    rsp->status = fwpb_status_ERROR;
    LOGE("RALH: no session");
    ralh_clear_all();
    proto_send_rsp(cmd, rsp);
    return false;
  }

  fwpb_recovery_authorize_lost_hw_rsp* out =
    &rsp->msg.get_confirmation_result_rsp.result.recovery_authorize_lost_hw_result;

  // 1. Seal DDK private key if present
  if (ralh_session.has_ddk) {
    if (!seal_bytes(ralh_session.ddk_private_key, AES_256_LENGTH_BYTES, &out->sealed_ddk_data)) {
      LOGE("RALH: seal DDK fail");
      goto fail;
    }
    out->has_sealed_ddk_data = true;
  }

  // 2. Sign UpdateDescriptorBackups SAP proof
  if (!sign_sap_proof("UpdateDescriptorBackups", ralh_session.descriptor_backups_bindings,
                      ralh_session.action_proof_version, out->descriptor_backups_signature.bytes)) {
    LOGE("RALH: SAP desc backups fail");
    rsp->status = fwpb_status_SIGNING_FAILED;
    goto fail;
  }
  out->descriptor_backups_signature.size = ECC_SIG_SIZE;

  // 3. Sign RotateSpendingKeyset SAP proof
  if (!sign_sap_proof("RotateSpendingKeyset", ralh_session.activate_keyset_bindings,
                      ralh_session.action_proof_version, out->activate_keyset_signature.bytes)) {
    LOGE("RALH: SAP ks fail");
    rsp->status = fwpb_status_SIGNING_FAILED;
    goto fail;
  }
  out->activate_keyset_signature.size = ECC_SIG_SIZE;

  rsp->msg.get_confirmation_result_rsp.which_result =
    fwpb_get_confirmation_result_rsp_recovery_authorize_lost_hw_result_tag;
  rsp->status = fwpb_status_SUCCESS;
  ralh_clear_all();
  ui_show_confirmation("Success", false);  // Don't lock -- verifyKeysAndBuildDescriptor follows
  proto_send_rsp(cmd, rsp);
  return true;

fail:
  if (rsp->status != fwpb_status_ERROR && rsp->status != fwpb_status_SIGNING_FAILED &&
      rsp->status != fwpb_status_KEY_DERIVATION_FAILED &&
      rsp->status != fwpb_status_INVALID_ARGUMENT) {
    rsp->status = fwpb_status_ERROR;
  }
  ralh_clear_all();
  proto_send_rsp(cmd, rsp);
  return false;
}

// ===========================================================================
// 4. Upgrade Authorize W3
// ===========================================================================

typedef struct {
  uint8_t ddk_private_key[AES_256_LENGTH_BYTES];
  bool has_ddk;
  fwpb_sealed_data sealed_ssek_for_decryption;
  bool has_sealed_ssek_for_decryption;
  char descriptor_backups_bindings[256];
  char activate_keyset_bindings[256];
  uint32_t action_proof_version;
  bool valid;
} uaw3_session_t;

static SHARED_TASK_BSS uaw3_session_t uaw3_session = {0};

static void uaw3_clear(void) {
  memzero(&uaw3_session, sizeof(uaw3_session));
}

static void uaw3_clear_all(void) {
  confirmation_manager_clear();
  uaw3_clear();
}

void recovery_composites_upgrade_authorize_w3_handle_init(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->status = fwpb_status_ERROR;

  uaw3_clear_all();

  fwpb_upgrade_authorize_w3_cmd* c = &cmd->msg.upgrade_authorize_w3_cmd;

  // Stash DDK private key (optional)
  if (c->ddk_private_key.size > 0) {
    if (c->ddk_private_key.size != AES_256_LENGTH_BYTES) {
      LOGE("UAW3: bad DDK size");
      rsp->status = fwpb_status_INVALID_ARGUMENT;
      goto out;
    }
    memcpy(uaw3_session.ddk_private_key, c->ddk_private_key.bytes, AES_256_LENGTH_BYTES);
    uaw3_session.has_ddk = true;
  }
  if (c->sealed_ssek_for_decryption.data.size > 0) {
    memcpy(&uaw3_session.sealed_ssek_for_decryption, &c->sealed_ssek_for_decryption,
           sizeof(fwpb_sealed_data));
    uaw3_session.has_sealed_ssek_for_decryption = true;
  }

  // Stash bindings (reject if too long to avoid silent truncation)
  if (strnlen(c->descriptor_backups_bindings, sizeof(uaw3_session.descriptor_backups_bindings)) >=
      sizeof(uaw3_session.descriptor_backups_bindings)) {
    LOGE("UAW3: desc OOB");
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }
  if (strnlen(c->activate_keyset_bindings, sizeof(uaw3_session.activate_keyset_bindings)) >=
      sizeof(uaw3_session.activate_keyset_bindings)) {
    LOGE("UAW3: ks OOB");
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }
  strncpy(uaw3_session.descriptor_backups_bindings, c->descriptor_backups_bindings,
          sizeof(uaw3_session.descriptor_backups_bindings) - 1);
  strncpy(uaw3_session.activate_keyset_bindings, c->activate_keyset_bindings,
          sizeof(uaw3_session.activate_keyset_bindings) - 1);
  uaw3_session.action_proof_version = c->action_proof_version;
  uaw3_session.valid = true;

  if (!create_confirmation_with_prompt(cmd, rsp, CONFIRMATION_TYPE_UPGRADE_AUTHORIZE_W3,
                                       SAP_ACTION_UPGRADE_WALLET)) {
    uaw3_clear();
    goto out;
  }

out:
  proto_send_rsp(cmd, rsp);
}

static bool uaw3_confirmation_result_handler(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd;
  fwpb_wallet_rsp* rsp;

  int result = validate_confirmation(message, &cmd, &rsp);
  if (result <= 0) {
    if (result < 0)
      uaw3_clear_all();
    return (result == 0);
  }

  if (!uaw3_session.valid) {
    rsp->status = fwpb_status_ERROR;
    LOGE("UAW3: no session");
    uaw3_clear_all();
    proto_send_rsp(cmd, rsp);
    return false;
  }

  fwpb_upgrade_authorize_w3_rsp* out =
    &rsp->msg.get_confirmation_result_rsp.result.upgrade_authorize_w3_result;

  // 1. Seal DDK private key if present
  if (uaw3_session.has_ddk) {
    if (!seal_bytes(uaw3_session.ddk_private_key, AES_256_LENGTH_BYTES, &out->sealed_ddk_data)) {
      LOGE("UAW3: seal DDK fail");
      goto fail;
    }
    out->has_sealed_ddk_data = true;
  }

  // 2. Unseal historical descriptor-backup SSEK if present
  if (uaw3_session.has_sealed_ssek_for_decryption) {
    uint8_t unsealed[AES_256_LENGTH_BYTES] = {0};
    if (!sealed_data_unseal(&uaw3_session.sealed_ssek_for_decryption, unsealed, sizeof(unsealed))) {
      LOGE("UAW3: unseal SSEK fail");
      goto fail;
    }
    memcpy(out->unsealed_ssek.bytes, unsealed, sizeof(unsealed));
    out->unsealed_ssek.size = sizeof(unsealed);
    memzero(unsealed, sizeof(unsealed));
  }

  // 3. Sign UpdateDescriptorBackups SAP proof
  if (!sign_sap_proof("UpdateDescriptorBackups", uaw3_session.descriptor_backups_bindings,
                      uaw3_session.action_proof_version, out->descriptor_backups_signature.bytes)) {
    LOGE("UAW3: SAP desc backups fail");
    rsp->status = fwpb_status_SIGNING_FAILED;
    goto fail;
  }
  out->descriptor_backups_signature.size = ECC_SIG_SIZE;

  // 4. Sign RotateSpendingKeyset SAP proof
  if (!sign_sap_proof("RotateSpendingKeyset", uaw3_session.activate_keyset_bindings,
                      uaw3_session.action_proof_version, out->activate_keyset_signature.bytes)) {
    LOGE("UAW3: SAP ks fail");
    rsp->status = fwpb_status_SIGNING_FAILED;
    goto fail;
  }
  out->activate_keyset_signature.size = ECC_SIG_SIZE;

  rsp->msg.get_confirmation_result_rsp.which_result =
    fwpb_get_confirmation_result_rsp_upgrade_authorize_w3_result_tag;
  rsp->status = fwpb_status_SUCCESS;
  uaw3_clear_all();
  ui_show_confirmation("Success", false);  // Don't lock -- verifyKeysAndBuildDescriptor follows
  proto_send_rsp(cmd, rsp);
  return true;

fail:
  if (rsp->status != fwpb_status_ERROR && rsp->status != fwpb_status_SIGNING_FAILED &&
      rsp->status != fwpb_status_KEY_DERIVATION_FAILED &&
      rsp->status != fwpb_status_INVALID_ARGUMENT) {
    rsp->status = fwpb_status_ERROR;
  }
  uaw3_clear_all();
  proto_send_rsp(cmd, rsp);
  return false;
}

// ===========================================================================
// Registration
// ===========================================================================

void recovery_composites_register_handlers(void) {
  confirmation_manager_register_result_handler(CONFIRMATION_TYPE_SIGN_CHALLENGE_AND_SEAL_SEKS,
                                               scas_confirmation_result_handler);
  confirmation_manager_register_result_handler(CONFIRMATION_TYPE_RECOVERY_AUTHORIZE_LOST_APP,
                                               rala_confirmation_result_handler);
  confirmation_manager_register_result_handler(CONFIRMATION_TYPE_RECOVERY_AUTHORIZE_LOST_HW,
                                               ralh_confirmation_result_handler);
  confirmation_manager_register_result_handler(CONFIRMATION_TYPE_UPGRADE_AUTHORIZE_W3,
                                               uaw3_confirmation_result_handler);
}

void recovery_composites_clear_sessions(void) {
  scas_clear();
  rala_clear();
  ralh_clear();
  uaw3_clear();
}
