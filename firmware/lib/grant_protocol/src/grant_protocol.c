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
#include "wallet.h"

#include <string.h>

// We don't have a POSIX (and thus unit testable) implementation of hal/sysinfo,
// so we extern the function and fake it in the tests.
extern void sysinfo_chip_id_read(uint8_t* chip_id_out, uint32_t* length_out);

static const uint8_t WIK_TEST_PUBKEY[] = {
  0x03, 0x07, 0x84, 0x51, 0xe0, 0xc1, 0xe1, 0x27, 0x43, 0xd2, 0xfd,
  0xd9, 0x3a, 0xe7, 0xd0, 0x3d, 0x5c, 0xf7, 0x81, 0x3d, 0x2f, 0x61,
  0x2d, 0xe1, 0x09, 0x04, 0xe1, 0xc6, 0xa0, 0xb8, 0x7f, 0x70, 0x71,
};

static const uint8_t WIK_PROD_PUBKEY[] = {
  0x02, 0x95, 0x21, 0x6a, 0x2e, 0x0b, 0x54, 0xb3, 0x82, 0xcc, 0x39,
  0x38, 0xe2, 0x07, 0x29, 0x8d, 0x21, 0xcb, 0x8c, 0x5f, 0x68, 0x6f,
  0x78, 0xb0, 0x5d, 0x9f, 0x14, 0xb4, 0xe4, 0x66, 0x9e, 0x56, 0x0f,
};

STATIC_VISIBLE_FOR_TESTING struct {
  const uint8_t* wik_pubkey;
  grant_request_t outstanding_request;  // Entire grant request, including the signature.
} grant_ctx = {
  .wik_pubkey = NULL,
  .outstanding_request = {0},
};

void grant_protocol_init(bool is_production) {
  if (is_production) {
    grant_ctx.wik_pubkey = WIK_PROD_PUBKEY;
  } else {
    grant_ctx.wik_pubkey = WIK_TEST_PUBKEY;
  }
}

static bool sign_with_hw_auth_key(uint8_t* message, uint32_t message_len, uint8_t* signature) {
  ASSERT(message && message_len && signature);

  extended_key_t key_priv = {0};
  if (!wallet_get_w1_auth_key(&key_priv)) {
    LOGE("Failed to derive auth key");
    return false;
  }

  return bip32_sign(&key_priv, message, signature);
}

static grant_protocol_result_t sign_request(grant_request_t* request) {
  ASSERT(request);

  // We sign "BKGrantReq" + the serialized request.
  uint8_t signable_request[GRANT_REQUEST_SIGNABLE_LEN] = {0};
  memcpy(signable_request, GRANT_REQUEST_LABEL, GRANT_REQUEST_LABEL_LEN);
  memcpy(signable_request + GRANT_REQUEST_LABEL_LEN, request,
         GRANT_REQUEST_EXCLUDING_SIGNATURE_LEN);

  if (!sign_with_hw_auth_key(signable_request, sizeof(signable_request), request->signature)) {
    LOGE("Failed to sign request");
    return GRANT_RESULT_ERROR_SIGNING;
  }

  return GRANT_RESULT_OK;
}

static grant_protocol_result_t verify_grant_signature(const grant_t* grant,
                                                      const grant_request_t* request) {
  ASSERT(grant && request);

  // Verify the signature over the serialized request.
  // This is the data up to (but not including) the signature field, along with the "BKGrant" label.
  uint8_t signed_serialized_grant[GRANT_SIGNABLE_LEN] = {0};
  memcpy(signed_serialized_grant, GRANT_LABEL, GRANT_LABEL_LEN);  // Prepend the label.
  memcpy(signed_serialized_grant + GRANT_LABEL_LEN, grant, GRANT_EXCLUDING_SIGNATURE_LEN);

  if (!crypto_ecc_secp256k1_verify_signature(grant_ctx.wik_pubkey, signed_serialized_grant,
                                             GRANT_SIGNABLE_LEN, grant->signature)) {
    LOGE("Failed to verify grant signature");
    return GRANT_RESULT_ERROR_VERIFICATION;
  }

  return GRANT_RESULT_OK;
}

static grant_protocol_result_t delete_outstanding_request(void) {
  grant_storage_delete_request();  // Does nothing if the file doesn't exist.
  memset(&grant_ctx.outstanding_request, 0, sizeof(grant_request_t));
  return GRANT_RESULT_OK;
}

static void perform_action(grant_action_t action) {
  switch (action) {
    case ACTION_FINGERPRINT_RESET:
      LOGD("Presenting grant for fingerprint reset");
      ipc_send_empty(auth_port, IPC_AUTH_PRESENT_GRANT_FOR_FINGERPRINT_ENROLLMENT);
      return;
    default:
      ASSERT_LOG(false, "Invalid action");
      return;
  }
}

static bool get_original_request(grant_action_t action, grant_request_t* out_request) {
  LOGD("Action: %d", action);
  switch (action) {
    case ACTION_FINGERPRINT_RESET:
      // Use what we persisted in flash.
      LOGD("Reading original request from flash");
      return grant_storage_read_request(out_request) == GRANT_RESULT_OK;
    default:
      ASSERT_LOG(false, "Invalid action");
      return false;
  }
}

grant_protocol_result_t grant_protocol_create_request(grant_action_t action,
                                                      grant_request_t* out_request) {
  ASSERT(out_request);
  ASSERT(grant_ctx.wik_pubkey);

  out_request->version = GRANT_PROTOCOL_VERSION;
  out_request->action = action;

  uint32_t len = GRANT_DEVICE_ID_LEN;
  sysinfo_chip_id_read(out_request->device_id, &len);
  if (len != GRANT_DEVICE_ID_LEN) {
    LOGE("Failed to read device ID");
    return GRANT_RESULT_ERROR_INTERNAL;
  }

  if (!crypto_random(out_request->challenge, GRANT_CHALLENGE_LEN)) {
    LOGE("Failed to generate challenge");
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
      LOGE("Failed to write grant request");
      return result;
    }
  }

  return GRANT_RESULT_OK;
}

grant_protocol_result_t grant_protocol_verify_grant(const grant_t* grant) {
  ASSERT(grant);
  ASSERT(grant_ctx.wik_pubkey);

  grant_action_t claimed_action = ((grant_request_t*)grant->serialized_request)->action;

  grant_request_t original_request = {0};
  if (!get_original_request(claimed_action, &original_request)) {
    LOGE("Failed to get original request");
    return GRANT_RESULT_ERROR_INTERNAL;
  }

  grant_protocol_result_t result = GRANT_RESULT_ERROR_INTERNAL;

  if (grant->version != GRANT_PROTOCOL_VERSION ||
      original_request.version != GRANT_PROTOCOL_VERSION) {
    LOGE("Version mismatch: Grant=%u, Request=%u", grant->version, original_request.version);
    result = GRANT_RESULT_ERROR_VERSION_MISMATCH;
    goto out;
  }

  // Check that the grant request within the grant is exactly the same as the original request.
  if (memcmp(grant->serialized_request, &original_request, sizeof(grant_request_t)) != 0) {
    LOGE("Serialized request mismatch between grant and original request");
    result = GRANT_RESULT_ERROR_REQUEST_MISMATCH;
    goto out;
  }

  // Verify that the signature in the grant matches the original request
  result = verify_grant_signature(grant, &original_request);
  if (result != GRANT_RESULT_OK) {
    LOGE("Signature verification failed");
    goto out;
  }

  // All checks passed.
  perform_action(original_request.action);

  result = GRANT_RESULT_OK;

  // Note: outstanding grant request needs to be deleted still, but it only happens
  // when the action is successful (i.e. fingerprint reset is completed).

out:
  return result;
}

grant_protocol_result_t grant_protocol_delete_outstanding_request(void) {
  return delete_outstanding_request();
}
