#include "hash.h"

#include "assert.h"
#include "crypto_impl.h"
#include "ripemd160_impl.h"

#include <openssl/hmac.h>
#include <openssl/sha.h>

#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wdeprecated-declarations"

bool crypto_hash(const uint8_t* message, uint32_t message_size, uint8_t* digest,
                 uint32_t digest_size, hash_alg_t alg) {
  ASSERT(message && digest);
  switch (alg) {
    case ALG_SHA256:
      ASSERT(digest_size == SHA256_DIGEST_SIZE);
      SHA256(message, message_size, digest);
      break;
    case ALG_SHA512:
      ASSERT(digest_size == SHA512_DIGEST_SIZE);
      SHA512(message, message_size, digest);
      break;
    case ALG_HASH160:
      ASSERT(digest_size == HASH160_DIGEST_SIZE);
      uint8_t tmp_digest[SHA256_DIGEST_SIZE];
      SHA256(message, message_size, tmp_digest);
      // use the firmware impl. of ripemd160 to get some extra test coverage
      ASSERT(mbedtls_ripemd160(tmp_digest, sizeof(tmp_digest), digest));
      break;
    default:
      ASSERT(false);
  }
  return true;
}

bool crypto_hmac(const uint8_t* message, uint32_t message_size, key_handle_t* key, uint8_t* digest,
                 uint32_t digest_size, hash_alg_t alg) {
  ASSERT(message && key && digest);
  ASSERT(key->key.bytes);

  const EVP_MD* hash = NULL;
  switch (alg) {
    case ALG_SHA256:
      hash = EVP_sha256();
      break;
    case ALG_SHA512:
      hash = EVP_sha512();
      break;
    default:
      ASSERT(false);
  }

  unsigned int actual_digest_size = 0;
  HMAC(hash, key->key.bytes, key->key.size, message, message_size, digest, &actual_digest_size);
  ASSERT(actual_digest_size == digest_size);
  return true;
}

bool crypto_sha256_stream_init(void* ctx) {
  ASSERT(ctx);
  SHA256_Init((SHA256_CTX*)ctx);
  return true;
}
bool crypto_sha256_stream_update(void* ctx, uint8_t* buffer, uint32_t size) {
  ASSERT(ctx && buffer);
  SHA256_Update((SHA256_CTX*)ctx, buffer, size);
  return true;
}

bool crypto_sha256_stream_final(void* ctx, uint8_t* digest_out) {
  ASSERT(ctx && digest_out);
  SHA256_Final(digest_out, (SHA256_CTX*)ctx);
  return true;
}

#pragma GCC diagnostic pop
