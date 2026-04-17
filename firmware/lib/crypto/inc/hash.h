#pragma once

#include "key_management.h"

#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>

typedef enum {
  ALG_SHA256 = 0,
  ALG_SHA512 = 1,
  ALG_HASH160 = 2,
  ALG_MD5 = 3,
  ALG_SHA1 = 4,
} hash_alg_t;

// Opaque SHA-256 streaming context (112 bytes).
//
// Must be large enough for the underlying platform implementation:
//   - efr32: sl_se_sha256_multipart_context_t  (108 bytes)
//   - posix: SHA256_CTX from OpenSSL/LibreSSL   (112 bytes)
//
// Keep this struct's size >= the larger of the two so that a single
// allocation works on both targets without platform-specific ifdefs
// in callers.
typedef struct {
  uint32_t hash_type;  // Streaming-context discriminator
  uint32_t total[2];   // Bytes processed
  uint8_t state[32];   // Intermediate digest state
  uint8_t buffer[68];  // Data block (64 bytes + 4-byte pad to fit platform contexts)
} hash_stream_ctx_t;

#define SHA256_DIGEST_SIZE  (32u)
#define SHA512_DIGEST_SIZE  (64u)
#define HASH160_DIGEST_SIZE (20u)

bool crypto_hash(const uint8_t* message, uint32_t message_size, uint8_t* digest,
                 uint32_t digest_size, hash_alg_t alg);
bool crypto_hmac(const uint8_t* message, uint32_t message_size, key_handle_t* key, uint8_t* digest,
                 uint32_t digest_size, hash_alg_t alg);

// For efr32, ctx should be a hash_stream_ctx_t*.
// For posix, ctx should be a SHA256_CTX*.
bool crypto_sha256_stream_init(void* ctx);
bool crypto_sha256_stream_update(void* ctx, uint8_t* buffer, uint32_t size);
bool crypto_sha256_stream_final(void* ctx, uint8_t* digest_out);

static inline bool crypto_sha256d(void* digest, void* data, size_t size) {
  uint8_t tmp[SHA256_DIGEST_SIZE];
  return crypto_hash((uint8_t*)data, size, tmp, SHA256_DIGEST_SIZE, ALG_SHA256) &&
         crypto_hash(tmp, sizeof(tmp), (uint8_t*)digest, SHA256_DIGEST_SIZE, ALG_SHA256);
}
