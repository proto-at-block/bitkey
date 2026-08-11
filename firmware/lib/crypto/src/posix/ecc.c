#include "ecc.h"

#include "key_management.h"
#include "secp256k1.h"
#include "secure_engine.h"

#include <string.h>

secure_bool_t crypto_ecc_verify_hash(key_handle_t* key, const uint8_t* hash, uint32_t hash_size,
                                     const uint8_t signature[ECC_SIG_SIZE]) {
  if (key == NULL || hash == NULL || signature == NULL) {
    return SECURE_FALSE;
  }

  if (key->alg == ALG_ECC_P256) {
    sl_se_command_context_t cmd_ctx = {0};
    sl_se_key_descriptor_t key_desc = se_key_descriptor_for_key_handle(key);
    if (sl_se_init_command_context(&cmd_ctx) != SL_STATUS_OK) {
      return SECURE_FALSE;
    }
    return se_ecc_verify(&cmd_ctx, &key_desc, SL_SE_HASH_NONE, true, hash, hash_size, signature,
                         ECC_SIG_SIZE) == SL_STATUS_OK
             ? SECURE_TRUE
             : SECURE_FALSE;
  }

  if (key->alg != ALG_ECC_SECP256K1) {
    return SECURE_FALSE;
  }

  // Create a verify-only context (lightweight)
  secp256k1_context* verify_ctx = secp256k1_context_create(SECP256K1_CONTEXT_VERIFY);
  if (verify_ctx == NULL) {
    return SECURE_FALSE;
  }

  secp256k1_pubkey pubkey;
  secp256k1_ecdsa_signature sig;
  secure_bool_t result = SECURE_FALSE;

  // Parse public key (SEC1 compressed or uncompressed format)
  if (!secp256k1_ec_pubkey_parse(verify_ctx, &pubkey, key->key.bytes, key->key.size)) {
    goto cleanup;
  }

  // Parse signature (compact r||s format, 64 bytes)
  if (!secp256k1_ecdsa_signature_parse_compact(verify_ctx, &sig, signature)) {
    goto cleanup;
  }

  // Verify the signature
  if (secp256k1_ecdsa_verify(verify_ctx, &sig, hash, &pubkey)) {
    result = SECURE_TRUE;
  }

cleanup:
  secp256k1_context_destroy(verify_ctx);
  return result;
}

secure_bool_t crypto_ecc_sign_hash(key_handle_t* privkey, uint8_t* hash, uint32_t hash_size,
                                   uint8_t signature[ECC_SIG_SIZE]) {
  if (privkey == NULL || hash == NULL || signature == NULL) {
    return SECURE_FALSE;
  }

  if (privkey->alg == ALG_ECC_P256) {
    sl_se_command_context_t cmd_ctx = {0};
    sl_se_key_descriptor_t key_desc = se_key_descriptor_for_key_handle(privkey);
    if (sl_se_init_command_context(&cmd_ctx) != SL_STATUS_OK) {
      return SECURE_FALSE;
    }
    return se_ecc_sign(&cmd_ctx, &key_desc, SL_SE_HASH_NONE, true, hash, hash_size, signature,
                       ECC_SIG_SIZE) == SL_STATUS_OK
             ? SECURE_TRUE
             : SECURE_FALSE;
  }

  if (crypto_ecc_secp256k1_ecdsa_sign_hash32(privkey, hash, signature, ECC_SIG_SIZE)) {
    return SECURE_TRUE;
  }
  return SECURE_FALSE;
}
