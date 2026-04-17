#include "ecc.h"
#include "hash.h"
#include "log.h"
#include "sign_action_proof_core.h"
#include "wallet.h"
#include "wallet.pb.h"
#include "wstring.h"

#include <string.h>

int sap_sign(sap_session_t* session) {
  session->sign_attempted = true;

  int payload_len = sap_build_payload(&session->pending_data, session->payload_buffer,
                                      sizeof(session->payload_buffer));
  if (payload_len <= 0) {
    LOGE("SAP build fail");
    session->sign_result = fwpb_status_ERROR;
    return session->sign_result;
  }

  uint8_t hash[SHA256_DIGEST_SIZE] = {0};
  if (!crypto_hash(session->payload_buffer, (uint32_t)payload_len, hash, sizeof(hash),
                   ALG_SHA256)) {
    LOGE("SAP hash fail");
    session->sign_result = fwpb_status_ERROR;
    return session->sign_result;
  }

  extended_key_t auth_key __attribute__((__cleanup__(bip32_zero_key)));
  if (!wallet_get_w1_auth_key(&auth_key)) {
    LOGE("SAP key fail");
    session->sign_result = fwpb_status_KEY_DERIVATION_FAILED;
    return session->sign_result;
  }

  uint8_t sig[ECC_SIG_SIZE] = {0};
  if (!bip32_sign(&auth_key, hash, sig)) {
    LOGE("SAP sign fail");
    session->sign_result = fwpb_status_SIGNING_FAILED;
    return session->sign_result;
  }

  memcpy(session->signature, sig, ECC_SIG_SIZE);
  memzero(sig, sizeof(sig));
  memzero(hash, sizeof(hash));
  session->signed_ok = true;
  session->sign_result = fwpb_status_SUCCESS;
  return session->sign_result;
}
