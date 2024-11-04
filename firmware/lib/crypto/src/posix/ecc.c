#include "ecc.h"

secure_bool_t crypto_ecc_verify_hash(key_handle_t* key, uint8_t* hash, uint32_t hash_size,
                                     uint8_t signature[ECC_SIG_SIZE]) {
  // Just a stub for now.
  return SECURE_TRUE;
}

secure_bool_t crypto_ecc_sign_hash(key_handle_t* privkey, uint8_t* hash, uint32_t UNUSED(hash_size),
                                   uint8_t signature[ECC_SIG_SIZE]) {
  if (crypto_ecc_secp256k1_ecdsa_sign_hash32(privkey, hash, signature, ECC_SIG_SIZE)) {
    return SECURE_TRUE;
  }
  return SECURE_FALSE;
}
