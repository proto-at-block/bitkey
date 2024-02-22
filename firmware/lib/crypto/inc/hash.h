#pragma once

#include "key_management.h"

#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>

typedef enum {
  ALG_SHA256 = 0,
  ALG_SHA512 = 1,
  ALG_HASH160 = 2,
} hash_alg_t;

typedef struct {
  uint32_t hash_type;  // Hash streaming context
  uint32_t total[2];   // Number of bytes processed
  uint8_t state[32];   // Intermediate digest state
  uint8_t buffer[64];  // Data block being processed
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
