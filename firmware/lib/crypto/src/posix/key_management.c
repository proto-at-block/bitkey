#include "key_management.h"

#include "aes.h"
#include "assert.h"
#include "attributes.h"
#include "secure_rng.h"

#include <stdlib.h>
#include <string.h>

static uint8_t fake_se_kek_buf[AES_256_LENGTH_BYTES] = {
  1, 2, 3, 4, 5, 6, 7, 8, 1, 2, 3, 4, 5, 6, 8, 1, 2, 3, 4, 5, 6, 7, 8, 1, 2, 3, 4, 5, 6, 8,
};

static key_handle_t fake_se_kek = {
  .alg = ALG_AES_256,
  .storage_type = KEY_STORAGE_INTERNAL_IMMUTABLE,
  .key.bytes = &fake_se_kek_buf[0],
  .key.size = sizeof(fake_se_kek_buf),
  .acl = SE_KEY_FLAG_NON_EXPORTABLE,
};

static bool se_wrap(uint8_t* plaintext_key, size_t key_size) {
  bool result = false;

  uint8_t* blob = malloc(key_size + SE_WRAPPED_KEY_OVERHEAD);

  uint8_t* iv = &blob[0];
  uint8_t* ciphertext = &blob[AES_GCM_IV_LENGTH];
  uint8_t* tag = &blob[AES_GCM_IV_LENGTH + key_size];

  if (!crypto_random(iv, AES_GCM_IV_LENGTH)) {
    goto out;
  }

  if (!aes_gcm_encrypt((uint8_t*)plaintext_key, ciphertext, key_size, iv, tag, NULL, 0,
                       &fake_se_kek)) {
    goto out;
  }

  result = true;

  memcpy(plaintext_key, blob, key_size + SE_WRAPPED_KEY_OVERHEAD);

out:
  free(blob);
  return result;
}

bool generate_key(key_handle_t* key) {
  ASSERT(key);

  uint32_t key_size = 0;
  switch (key->alg) {
    case ALG_AES_128:
      key_size = AES_128_LENGTH_BYTES;
      ASSERT(crypto_random(key->key.bytes, AES_128_LENGTH_BYTES));
      break;
    case ALG_AES_256:
      key_size = AES_256_LENGTH_BYTES;
      ASSERT(crypto_random(key->key.bytes, AES_256_LENGTH_BYTES));
      break;
    default:
      ASSERT(false);
  }

  switch (key->storage_type) {
    case KEY_STORAGE_EXTERNAL_PLAINTEXT:
      // nothing to do
      break;
    case KEY_STORAGE_EXTERNAL_WRAPPED:
      ASSERT(se_wrap(key->key.bytes, key_size));
      break;
    default:
      ASSERT(false);
  }

  return true;
}

uint32_t key_management_custom_domain_prepare(key_algorithm_t UNUSED(alg), uint8_t* UNUSED(buffer),
                                              uint32_t UNUSED(size)) {
  return 0;
}
