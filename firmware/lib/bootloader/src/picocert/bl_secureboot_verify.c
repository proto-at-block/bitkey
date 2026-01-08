#include "attributes.h"
#include "bl_secureboot.h"
#include "bl_secureboot_impl.h"
#include "ecc.h"
#include "hash.h"
#include "key_management.h"
#include "secutils.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

NO_OPTIMIZE static bool bl_secureboot_verify_hash(const uint8_t* data, uint32_t data_len,
                                                  uint8_t* digest, uint32_t digest_len) {
  return crypto_hash(data, data_len, digest, digest_len, ALG_SHA256);
}

NO_OPTIMIZE static bool bl_secureboot_verify_ecc(const uint8_t* key, size_t key_size,
                                                 const uint8_t* hash, uint32_t hash_len,
                                                 const uint8_t* signature) {
  volatile secure_bool_t verified = SECURE_FALSE;

  key_handle_t key_handle = {
    .alg = ALG_ECC_P256,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = (uint8_t*)key,
    .key.size = key_size,
  };

  verified = crypto_ecc_verify_hash(&key_handle, hash, hash_len, signature);
  SECURE_IF_FAILIN(verified != SECURE_TRUE) { return false; }

  return true;
}

NO_OPTIMIZE secure_bool_t bl_secureboot_verify(app_certificate_t* cert, uint8_t sig[ECC_SIG_SIZE],
                                               uint8_t* data, size_t length) {
  if (!cert || !sig || !data || !length) {
    return SECURE_FALSE;
  }

  uint8_t hash[SHA256_DIGEST_SIZE] = {0};
  if (!crypto_hash(data, length, hash, sizeof(hash), ALG_SHA256)) {
    return SECURE_FALSE;
  }

  picocert_context_t ctx;
  volatile picocert_err_t err =
    picocert_init_context(&ctx, bl_secureboot_verify_hash, bl_secureboot_verify_ecc, NULL);
  SECURE_IF_FAILIN(err != PICOCERT_OK) { return SECURE_FALSE; }

  err = picocert_verify_hash(&ctx, cert, hash, sig);
  SECURE_IF_FAILIN(err != PICOCERT_OK) { return SECURE_FALSE; }

  return SECURE_TRUE;
}
