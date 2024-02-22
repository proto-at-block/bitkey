#include "secure_engine.h"
#include "secutils.h"
#include "sli_se_manager_internal.h"

#include <string.h>

#define LABEL          "ATV1"
#define LABEL_SIZE     (4)
#define CHALLENGE_SIZE (16)

sl_status_t se_sign_with_device_identity_key(uint8_t* data, uint32_t size, uint8_t* signature,
                                             uint32_t signature_size) {
  sl_se_command_context_t attestation_cmd_ctx = {0};

  sl_se_key_descriptor_t private_device_key = {
    .type = SL_SE_KEY_TYPE_ECC_P256,
    .flags = SL_SE_KEY_FLAG_IS_DEVICE_GENERATED | SL_SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PRIVATE_KEY |
             SL_SE_KEY_FLAG_ASYMMETRIC_SIGNING_ONLY,
    .storage =
      {
        .method = SL_SE_KEY_STORAGE_INTERNAL_IMMUTABLE,
        .location =
          {
            .slot = SL_SE_KEY_SLOT_APPLICATION_ATTESTATION_KEY,
          },
      },
  };

  if (sl_se_init_command_context(&attestation_cmd_ctx) != SL_STATUS_OK) {
    goto fail;
  }

  if (se_ecc_sign(&attestation_cmd_ctx, &private_device_key, SL_SE_HASH_SHA256, false, data, size,
                  signature, signature_size) != SL_STATUS_OK) {
    goto fail;
  }

  memset(&attestation_cmd_ctx, 0, sizeof(attestation_cmd_ctx));

  // Export pubkey and verify after signing
  uint8_t pubkey[64] = {0};
  if (se_read_pubkey(SL_SE_KEY_TYPE_IMMUTABLE_ATTESTATION, pubkey, sizeof(pubkey)) !=
      SL_STATUS_OK) {
    goto fail;
  }

  sl_se_key_descriptor_t public_device_key = {
    .type = SL_SE_KEY_TYPE_ECC_P256,
    .flags =
      SL_SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PUBLIC_KEY | SL_SE_KEY_FLAG_ASYMMETRIC_SIGNING_ONLY,
    .storage =
      {
        .method = SL_SE_KEY_STORAGE_EXTERNAL_PLAINTEXT,
        .location =
          {
            .buffer.pointer = pubkey,
            .buffer.size = sizeof(pubkey),
          },
      },
  };

  if (se_ecc_verify(&attestation_cmd_ctx, &public_device_key, SL_SE_HASH_SHA256, false, data, size,
                    signature, signature_size) != SL_STATUS_OK) {
    goto fail;
  }

  return SL_STATUS_OK;

fail:
  memzero(signature, signature_size);
  return SL_STATUS_FAIL;
}

sl_status_t se_sign_challenge(uint8_t* challenge, uint32_t challenge_size, uint8_t* signature,
                              uint32_t signature_size) {
  if (challenge_size != CHALLENGE_SIZE) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  uint8_t challenge_and_label[LABEL_SIZE + CHALLENGE_SIZE] = {0};
  memcpy(challenge_and_label, LABEL, LABEL_SIZE);
  memcpy(&challenge_and_label[LABEL_SIZE], challenge, challenge_size);

  return se_sign_with_device_identity_key(challenge_and_label, sizeof(challenge_and_label),
                                          signature, signature_size);
}
