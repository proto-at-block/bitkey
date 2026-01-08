#include "key_management.h"

#include "assert.h"
#include "curve25519.h"
#include "ecc.h"
#include "mcu_pka.h"
#include "secure_rng.h"

#include <stdlib.h>
#include <string.h>

bool generate_key(key_handle_t* key) {
  ASSERT(key != NULL);
  ASSERT(key->storage_type == KEY_STORAGE_EXTERNAL_PLAINTEXT);
  ASSERT(key->key.bytes != NULL);

  switch (key->alg) {
    case ALG_ECC_X25519:
      ASSERT(key->key.size == EC_PRIVKEY_SIZE_X25519);
      return curve25519_generate_key(key->key.bytes, key->key.size);

    case ALG_ECC_P256: {
      ASSERT(key->key.size == ECC_PRIVKEY_SIZE);
      return crypto_ecc_generate_random_scalar(key->key.bytes, key->key.size);
    }

    default:
      ASSERT(false);
  }
  return false;
}

bool export_pubkey(key_handle_t* key_in, key_handle_t* key_out) {
  ASSERT(key_in != NULL);
  ASSERT(key_in->storage_type == KEY_STORAGE_EXTERNAL_PLAINTEXT);
  ASSERT(key_in->key.bytes != NULL);

  ASSERT(key_out != NULL);
  ASSERT(key_out->storage_type == KEY_STORAGE_EXTERNAL_PLAINTEXT);
  ASSERT(key_out->key.bytes != NULL);

  switch (key_in->alg) {
    case ALG_ECC_X25519:
      ASSERT(key_in->key.size == EC_PRIVKEY_SIZE_X25519);
      ASSERT(key_out->alg == ALG_ECC_X25519);
      ASSERT(key_out->key.size == EC_PUBKEY_SIZE_X25519);
      curve25519_get_public_key(key_out->key.bytes, key_in->key.bytes);
      return true;

    case ALG_ECC_P256: {
      ASSERT(key_in->key.size == ECC_PRIVKEY_SIZE);
      ASSERT(key_out->alg == ALG_ECC_P256);
      ASSERT(key_out->key.size == ECC_PUBKEY_SIZE_ECDSA_UNCOMPRESSED);
      return crypto_ecc_derive_public_key(key_in, key_out);
    }
    default:
      ASSERT(false);
  }
  return false;
}

// TODO SECENG-8952: Implement device identity cert on stm32
bool crypto_sign_with_device_identity(uint8_t* data, uint32_t data_size, uint8_t* signature,
                                      uint32_t signature_size) {
  (void)data_size;
  ASSERT(data != NULL);
  ASSERT(signature != NULL);
  // Zero until implemented in case an uninitialized buffer is passed for the signature
  memset(signature, 0, signature_size);
  return true;
}
