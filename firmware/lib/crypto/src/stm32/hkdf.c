#include "hkdf.h"

#include "assert.h"
#include "crypto_stm32_common.h"
#include "mcu_hash.h"

#include <string.h>

bool crypto_hkdf(key_handle_t* key_in, hash_alg_t hash, uint8_t const* salt, size_t salt_len,
                 uint8_t const* info, size_t info_len, key_handle_t* key_out) {
  ASSERT(key_in != NULL);
  ASSERT(key_in->key.bytes != NULL);
  ASSERT(key_out != NULL);
  ASSERT(key_out->key.bytes != NULL);
  ASSERT(info_len <= CRYPTO_HKDF_INFO_MAX_LEN);

  // It would not be difficult to support other algorithms
  // We would just need a way to get their digest size for the salt == NULL case
  ASSERT(hash == ALG_SHA256);

  // RFC 5869 limit since counter value is stored in a single byte
  ASSERT(key_out->key.size <= 255 * SHA256_DIGEST_SIZE);

  // HKDF-Extract: PRK = HMAC-Hash(salt, IKM)
  // When salt is null hkdf uses a zero salt of the hash digest size
  // Using the prk buffer for this to save space
  uint8_t prk[SHA256_DIGEST_SIZE];
  if (salt == NULL) {
    memset(prk, 0, SHA256_DIGEST_SIZE);
    salt = prk;
    salt_len = SHA256_DIGEST_SIZE;
  }

  if (!mcu_hash_hmac(crypto_alg_type(hash), key_in->key.bytes, key_in->key.size, salt, salt_len,
                     prk, SHA256_DIGEST_SIZE)) {
    return false;
  }

  // HKDF-Expand: OKM = T(1) | T(2) | T(3) | ... | T(N)
  // where:
  //   T(0) = empty string (zero length)
  //   T(N) = HMAC-Hash(PRK, T(N-1) | info | N)

  size_t offset = 0;
  uint8_t counter = 0;
  uint8_t message[SHA256_DIGEST_SIZE + CRYPTO_HKDF_INFO_MAX_LEN + 1];

  while (offset < key_out->key.size) {
    // Counter starts at 1 for the first iteration
    counter++;

    // Build message: T(i-1) | info | counter
    // For all loops after the first T(i-1) will be written to the start of message
    size_t message_len = 0;
    if (counter > 1) {
      message_len += SHA256_DIGEST_SIZE;
    }

    // Append info
    if (info != NULL) {
      memcpy(&message[message_len], info, info_len);
      message_len += info_len;
    }

    // Append counter byte
    message[message_len] = counter;
    message_len += 1;

    // Compute T(i) = HMAC-Hash(PRK, T(i-1) | info | counter).
    // Writes T(i) to the start of the message buffer to avoid allocating additional space.
    if (!mcu_hash_hmac(crypto_alg_type(hash), message, message_len, prk, sizeof(prk), message,
                       SHA256_DIGEST_SIZE)) {
      return false;
    }

    // Copy as much as needed to output
    size_t const to_copy = offset + SHA256_DIGEST_SIZE <= key_out->key.size
                             ? SHA256_DIGEST_SIZE
                             : key_out->key.size - offset;
    memcpy(&key_out->key.bytes[offset], message, to_copy);
    offset += to_copy;
  }

  return true;
}
