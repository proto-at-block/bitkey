#include "hkdf.h"

#include "assert.h"
#include "hash.h"

#include <string.h>

bool crypto_hkdf(key_handle_t* key_in, hash_alg_t hash, uint8_t const* salt, size_t salt_len,
                 uint8_t const* info, size_t info_len, key_handle_t* key_out) {
  ASSERT(key_in != NULL);
  ASSERT(key_in->key.bytes != NULL);
  ASSERT(key_out != NULL);
  ASSERT(key_out->key.bytes != NULL);
  ASSERT(info_len <= CRYPTO_HKDF_INFO_MAX_LEN);

  // Only SHA256 supported currently
  ASSERT(hash == ALG_SHA256);

  // RFC 5869 limit since counter value is stored in a single byte
  ASSERT(key_out->key.size <= 255 * SHA256_DIGEST_SIZE);

  // HKDF-Extract: PRK = HMAC-Hash(salt, IKM)
  uint8_t prk[SHA256_DIGEST_SIZE];
  uint8_t default_salt[SHA256_DIGEST_SIZE] = {0};

  const uint8_t* actual_salt = (salt != NULL) ? salt : default_salt;
  size_t actual_salt_len = (salt != NULL) ? salt_len : SHA256_DIGEST_SIZE;

  key_handle_t salt_key = {
    .alg = ALG_HMAC,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = (uint8_t*)actual_salt,
    .key.size = actual_salt_len,
  };

  if (!crypto_hmac(key_in->key.bytes, key_in->key.size, &salt_key, prk, sizeof(prk), ALG_SHA256)) {
    return false;
  }

  // HKDF-Expand: OKM = T(1) | T(2) | T(3) | ... | T(N)
  // where:
  //   T(0) = empty string (zero length)
  //   T(N) = HMAC-Hash(PRK, T(N-1) | info | N)

  key_handle_t prk_key = {
    .alg = ALG_HMAC,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = prk,
    .key.size = sizeof(prk),
  };

  size_t offset = 0;
  uint8_t counter = 0;
  uint8_t message[SHA256_DIGEST_SIZE + CRYPTO_HKDF_INFO_MAX_LEN + 1];
  uint8_t t[SHA256_DIGEST_SIZE];

  while (offset < key_out->key.size) {
    counter++;

    // Build message: T(i-1) | info | counter
    size_t message_len = 0;
    if (counter > 1) {
      memcpy(message, t, SHA256_DIGEST_SIZE);
      message_len += SHA256_DIGEST_SIZE;
    }

    if (info != NULL) {
      memcpy(&message[message_len], info, info_len);
      message_len += info_len;
    }

    message[message_len] = counter;
    message_len += 1;

    // Compute T(i) = HMAC-Hash(PRK, T(i-1) | info | counter)
    if (!crypto_hmac(message, message_len, &prk_key, t, sizeof(t), ALG_SHA256)) {
      return false;
    }

    // Copy as much as needed to output
    size_t to_copy = (offset + SHA256_DIGEST_SIZE <= key_out->key.size)
                       ? SHA256_DIGEST_SIZE
                       : key_out->key.size - offset;
    memcpy(&key_out->key.bytes[offset], t, to_copy);
    offset += to_copy;
  }

  return true;
}
