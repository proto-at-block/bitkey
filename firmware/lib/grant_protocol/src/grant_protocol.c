#include "grant_protocol.h"

#include "assert.h"
#include "attributes.h"
#include "bip32.h"
#include "grant_protocol_storage_impl.h"
#include "hex.h"
#include "ipc.h"
#include "log.h"
#include "policy.h"
#include "secure_rng.h"
#include "secutils.h"
#include "wallet.h"
#include "wsm_integrity_key.h"
#include "wstring.h"

#include <string.h>

// We don't have a POSIX (and thus unit testable) implementation of hal/sysinfo,
// so we extern the function and fake it in the tests.
extern void sysinfo_chip_id_read(uint8_t* chip_id_out, uint32_t* length_out);

STATIC_VISIBLE_FOR_TESTING struct {
  const uint8_t* wik_pubkey;
  grant_request_t outstanding_request;  // Entire grant request, including the signature.
} grant_ctx = {
  .wik_pubkey = NULL,
  .outstanding_request = {0},
};

void grant_protocol_init(bool is_production) {
  if (is_production) {
    grant_ctx.wik_pubkey = WSM_INTEGRITY_PROD_PUBKEY;
  } else {
    grant_ctx.wik_pubkey = WSM_INTEGRITY_TEST_PUBKEY;
  }
}

static bool sign_with_hw_auth_key(uint8_t* message, uint32_t message_len, uint8_t* signature) {
  ASSERT(message && message_len && signature);

  extended_key_t key_priv = {0};
  if (!wallet_get_w1_auth_key(&key_priv)) {
    LOGE("Auth key derive fail");
    memzero(&key_priv, sizeof(key_priv));
    return false;
  }

  uint8_t message_digest[SHA256_DIGEST_SIZE] = {0};
  if (!crypto_hash(message, message_len, message_digest, sizeof(message_digest), ALG_SHA256)) {
    LOGE("Msg hash fail");
    memzero(&key_priv, sizeof(key_priv));
    return false;
  }

  bool ret = bip32_sign(&key_priv, message_digest, signature);
  memzero(&key_priv, sizeof(key_priv));
  return ret;
}

static grant_protocol_result_t sign_request(grant_request_t* request) {
  ASSERT(request);

  // We sign "BKGrantReq" + the serialized request.
  uint8_t signable_request[GRANT_REQUEST_SIGNABLE_LEN] = {0};
  memcpy(signable_request, GRANT_REQUEST_LABEL, GRANT_REQUEST_LABEL_LEN);
  memcpy(signable_request + GRANT_REQUEST_LABEL_LEN, request,
         GRANT_REQUEST_EXCLUDING_SIGNATURE_LEN);

  if (!sign_with_hw_auth_key(signable_request, sizeof(signable_request), request->signature)) {
    LOGE("Request sign fail");
    return GRANT_RESULT_ERROR_SIGNING;
  }

  return GRANT_RESULT_OK;
}

static grant_protocol_result_t verify_app_signature(const grant_t* grant,
                                                    const grant_request_t* request) {
  ASSERT(grant && request);

  // Check if app auth pubkey exists
  uint8_t app_pubkey[33] = {0};
  if (!grant_storage_read_app_auth_pubkey(app_pubkey)) {
    LOGE("No app auth pubkey");
    return GRANT_RESULT_ERROR_NO_APP_PUBKEY;
  }

  // Build the message that app signed: "BKAppBind" || request_core
  // Request core is: version, device_id, challenge, action (excluding signature)
  uint8_t app_signed_message[APP_BIND_LABEL_LEN + GRANT_REQUEST_EXCLUDING_SIGNATURE_LEN] = {0};
  memcpy(app_signed_message, APP_BIND_LABEL, APP_BIND_LABEL_LEN);
  memcpy(app_signed_message + APP_BIND_LABEL_LEN, request, GRANT_REQUEST_EXCLUDING_SIGNATURE_LEN);

  if (!crypto_ecc_secp256k1_verify_signature(
        app_pubkey, app_signed_message, APP_BIND_LABEL_LEN + GRANT_REQUEST_EXCLUDING_SIGNATURE_LEN,
        grant->app_signature)) {
    LOGE("App sig verify fail");
    return GRANT_RESULT_ERROR_APP_VERIFICATION;
  }

  return GRANT_RESULT_OK;
}

static grant_protocol_result_t verify_grant_signature(const grant_t* grant) {
  ASSERT(grant);

  // Verify the WIK signature over version + serialized_request + app_signature + "BKGrant"
  uint8_t signed_serialized_grant[GRANT_SIGNABLE_LEN] = {0};
  memcpy(signed_serialized_grant, GRANT_LABEL, GRANT_LABEL_LEN);  // Prepend the label.
  memcpy(signed_serialized_grant + GRANT_LABEL_LEN, grant, GRANT_EXCLUDING_WSM_SIGNATURE_LEN);

  if (!wsm_verify_signature(signed_serialized_grant, GRANT_SIGNABLE_LEN, grant->wsm_signature)) {
    LOGE("WIK sig verify fail");
    return GRANT_RESULT_ERROR_VERIFICATION;
  }

  return GRANT_RESULT_OK;
}

static grant_protocol_result_t delete_outstanding_request(void) {
  grant_storage_delete_request();  // Does nothing if the file doesn't exist.
  memset(&grant_ctx.outstanding_request, 0, sizeof(grant_request_t));
  return GRANT_RESULT_OK;
}

static grant_protocol_result_t validate_action(grant_action_t action) {
  switch (action) {
    case ACTION_FINGERPRINT_RESET:
      return GRANT_RESULT_OK;
    default:
      LOGE("Invalid action: %d", action);
      return GRANT_RESULT_ERROR_INVALID_ARGUMENT;
  }
}

static grant_protocol_result_t perform_action(grant_action_t action) {
  grant_protocol_result_t validation_result = validate_action(action);
  if (validation_result != GRANT_RESULT_OK) {
    return validation_result;
  }

  switch (action) {
    case ACTION_FINGERPRINT_RESET:
      ipc_send_empty(auth_port, IPC_AUTH_PRESENT_GRANT_FOR_FINGERPRINT_ENROLLMENT);
      return GRANT_RESULT_OK;
    default:
      LOGE("Invalid action: %d", action);
      return GRANT_RESULT_ERROR_INTERNAL;
  }
}

grant_protocol_result_t grant_protocol_create_request(grant_action_t action,
                                                      grant_request_t* out_request) {
  ASSERT(out_request);
  ASSERT(grant_ctx.wik_pubkey);

  grant_protocol_result_t validation_result = validate_action(action);
  if (validation_result != GRANT_RESULT_OK) {
    return validation_result;
  }

  // For fingerprint reset, require app auth pubkey to be provisioned
  if (action == ACTION_FINGERPRINT_RESET) {
    if (!grant_storage_app_auth_pubkey_exists()) {
      LOGE("No app pubkey");
      return GRANT_RESULT_ERROR_NO_APP_PUBKEY;
    }
  }

  out_request->version = GRANT_PROTOCOL_VERSION;
  out_request->action = action;

  uint32_t len = GRANT_DEVICE_ID_LEN;
  sysinfo_chip_id_read(out_request->device_id, &len);
  if (len != GRANT_DEVICE_ID_LEN) {
    LOGE("Device ID read fail");
    return GRANT_RESULT_ERROR_INTERNAL;
  }

  if (!crypto_random(out_request->challenge, GRANT_CHALLENGE_LEN)) {
    LOGE("Challenge gen fail");
    return GRANT_RESULT_ERROR_INTERNAL;
  }

  grant_protocol_result_t result = sign_request(out_request);
  if (result != GRANT_RESULT_OK) {
    return result;
  }

  // Store the grant request.
  memcpy(&grant_ctx.outstanding_request, out_request, sizeof(grant_request_t));

  if (action == ACTION_FINGERPRINT_RESET) {
    result = grant_storage_write_request(out_request);
    if (result != GRANT_RESULT_OK) {
      LOGE("Grant req wr err");
      return result;
    }
  }

  return GRANT_RESULT_OK;
}

NO_OPTIMIZE grant_protocol_result_t grant_protocol_verify_grant(const grant_t* grant) {
  ASSERT(grant);
  ASSERT(grant_ctx.wik_pubkey);

  grant_request_t original_request = {0};
  // Load the trusted request first. Do not branch on the untrusted action
  // embedded in grant->serialized_request before the request is authenticated.
  grant_protocol_result_t get_request_result = grant_storage_read_request(&original_request);
  if (get_request_result != GRANT_RESULT_OK) {
    LOGE("Grant req rd err");
    return get_request_result;
  }

  grant_protocol_result_t result = GRANT_RESULT_ERROR_INTERNAL;

  if (grant->version != GRANT_PROTOCOL_VERSION ||
      original_request.version != GRANT_PROTOCOL_VERSION) {
    LOGE("Grant ver: %u!=%u", grant->version, original_request.version);
    result = GRANT_RESULT_ERROR_VERSION_MISMATCH;
    goto out;
  }

  // Check that the grant request within the grant is exactly the same as the original request.
  if (memcmp(grant->serialized_request, &original_request, sizeof(grant_request_t)) != 0) {
    LOGE("Grant request mismatch");
    result = GRANT_RESULT_ERROR_REQUEST_MISMATCH;
    goto out;
  }

  // Verify the app signature over the request core
  volatile grant_protocol_result_t app_sig_result = GRANT_RESULT_ERROR_INTERNAL;
  SECURE_DO_ONCE(app_sig_result = verify_app_signature(grant, &original_request));
  SECURE_IF_FAILIN(app_sig_result != GRANT_RESULT_OK) {
    result = app_sig_result;
    goto out;
  }

  // Verify the WIK signature over the grant (including app_signature)
  volatile grant_protocol_result_t wik_sig_result = GRANT_RESULT_ERROR_INTERNAL;
  SECURE_DO_ONCE(wik_sig_result = verify_grant_signature(grant));
  SECURE_IF_FAILIN(wik_sig_result != GRANT_RESULT_OK) {
    result = wik_sig_result;
    goto out;
  }

  // All checks passed; execute only supported, authenticated actions.
  result = perform_action(original_request.action);

  // Note: outstanding grant request needs to be deleted still, but it only happens
  // when the action is successful (i.e. fingerprint reset is completed).

out:
  return result;
}

grant_protocol_result_t grant_protocol_delete_outstanding_request(void) {
  return delete_outstanding_request();
}

grant_protocol_result_t grant_protocol_delete_app_auth_pubkey(void) {
  if (!grant_storage_delete_app_auth_pubkey()) {
    return GRANT_RESULT_ERROR_STORAGE;
  }
  return GRANT_RESULT_OK;
}
