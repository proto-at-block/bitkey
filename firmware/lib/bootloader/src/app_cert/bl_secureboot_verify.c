#include "attributes.h"
#include "bl_secureboot.h"
#include "bl_secureboot_impl.h"
#include "ecc.h"
#include "hash.h"
#include "key_management.h"
#include "secutils.h"

#include <stddef.h>
#include <stdint.h>

NO_OPTIMIZE secure_bool_t bl_secureboot_verify(app_certificate_t* cert, uint8_t sig[ECC_SIG_SIZE],
                                               uint8_t* data, size_t length) {
  if (!cert || !sig || !data || !length) {
    return SECURE_FALSE;
  }

  uint8_t hash[SHA256_DIGEST_SIZE] = {0};
  if (!crypto_hash(data, length, hash, sizeof(hash), ALG_SHA256)) {
    return SECURE_FALSE;
  }

  key_handle_t key = {
    .alg = ALG_ECC_P256,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = cert->key,
    .key.size = sizeof(cert->key),
    .acl = SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PUBLIC_KEY | SE_KEY_FLAG_ASYMMETRIC_SIGNING_ONLY,
  };

  volatile secure_bool_t verified = SECURE_FALSE;
  verified = crypto_ecc_verify_hash(&key, hash, SHA256_DIGEST_SIZE, sig);
  SECURE_IF_FAILIN(verified != SECURE_TRUE) { return SECURE_FALSE; }

  SECURE_DO_ONCE({
    verified = SECURE_FALSE;  // Reset to closed state
  });

  verified = crypto_ecc_verify_hash(&key, hash, SHA256_DIGEST_SIZE, sig);
  SECURE_IF_FAILIN(verified != SECURE_TRUE) { return SECURE_FALSE; }

  return verified;
}
