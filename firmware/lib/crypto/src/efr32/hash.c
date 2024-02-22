#include "hash.h"

#include "assert.h"
#include "crypto_impl.h"
#include "hex.h"
#include "ripemd160_impl.h"
#include "secure_engine.h"

#include <string.h>

inline sl_se_hash_type_t convert_hash_type(hash_alg_t alg) {
  sl_se_hash_type_t hash_type = SL_SE_HASH_NONE;
  switch (alg) {
    case ALG_SHA256:
      hash_type = SL_SE_HASH_SHA256;
      break;
    case ALG_SHA512:
      hash_type = SL_SE_HASH_SHA512;
      break;
    default:
      ASSERT(false);
  }
  return hash_type;
}

bool crypto_hash(const uint8_t* message, uint32_t message_size, uint8_t* digest,
                 uint32_t digest_size, hash_alg_t alg) {
  ASSERT(message && digest);

  sl_se_command_context_t cmd_ctx = {0};
  sl_status_t status = sl_se_init_command_context(&cmd_ctx);
  if (status != SL_STATUS_OK) {
    return false;
  }

  switch (alg) {
    case ALG_HASH160: {
      ASSERT(digest_size == HASH160_DIGEST_SIZE);
      uint8_t tmp_digest[SHA256_DIGEST_SIZE];
      status =
        se_hash(&cmd_ctx, SL_SE_HASH_SHA256, message, message_size, tmp_digest, sizeof(tmp_digest));
      if (status != SL_STATUS_OK) {
        return false;
      }
      if (!mbedtls_ripemd160(tmp_digest, sizeof(tmp_digest), digest)) {
        return false;
      }
      break;
    }
    default: {
      status =
        se_hash(&cmd_ctx, convert_hash_type(alg), message, message_size, digest, digest_size);
      break;
    }
  }

  return (status == SL_STATUS_OK);
}

bool crypto_hmac(const uint8_t* message, uint32_t message_size, key_handle_t* key, uint8_t* digest,
                 uint32_t digest_size, hash_alg_t alg) {
  ASSERT(message && key && digest);

  sl_se_command_context_t cmd_ctx = {0};
  sl_se_key_descriptor_t key_desc = se_key_descriptor_for_key_handle(key);
  sl_status_t status = sl_se_init_command_context(&cmd_ctx);
  if (status != SL_STATUS_OK) {
    return false;
  }

  status = se_hmac(&cmd_ctx, &key_desc, convert_hash_type(alg), message, message_size, digest,
                   digest_size);
  return (status == SL_STATUS_OK);
}

bool crypto_sha256_stream_init(void* ctx) {
  ASSERT(ctx);

  sl_se_command_context_t cmd_ctx = {0};
  sl_status_t status = sl_se_init_command_context(&cmd_ctx);
  if (status != SL_STATUS_OK) {
    return false;
  }

  return se_hash_sha256_multipart_starts((sl_se_sha256_multipart_context_t*)ctx, &cmd_ctx) ==
         SL_STATUS_OK;
}

bool crypto_sha256_stream_update(void* ctx, uint8_t* buffer, uint32_t size) {
  ASSERT(ctx && buffer);

  sl_se_command_context_t cmd_ctx = {0};
  sl_status_t status = sl_se_init_command_context(&cmd_ctx);
  if (status != SL_STATUS_OK) {
    return false;
  }

  return se_hash_multipart_update((sl_se_sha256_multipart_context_t*)ctx, &cmd_ctx, buffer, size) ==
         SL_STATUS_OK;
}

bool crypto_sha256_stream_final(void* ctx, uint8_t* digest_out) {
  ASSERT(ctx && digest_out);

  sl_se_command_context_t cmd_ctx = {0};
  sl_status_t status = sl_se_init_command_context(&cmd_ctx);
  if (status != SL_STATUS_OK) {
    return false;
  }

  return se_hash_multipart_finish((sl_se_sha256_multipart_context_t*)ctx, &cmd_ctx, digest_out,
                                  SHA256_DIGEST_SIZE) == SL_STATUS_OK;
}
