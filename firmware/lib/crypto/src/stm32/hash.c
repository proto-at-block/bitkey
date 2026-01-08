#include "hash.h"

#include "assert.h"
#include "crypto_stm32_common.h"
#include "mcu.h"
#include "mcu_hash.h"

#include <stdbool.h>
#include <stdint.h>

bool crypto_hash(const uint8_t* message, uint32_t message_size, uint8_t* digest,
                 uint32_t digest_size, hash_alg_t alg) {
  ASSERT(message != NULL);
  ASSERT(digest != NULL);

  const mcu_err_t err = mcu_hash(crypto_alg_type(alg), message, message_size, digest, digest_size);
  return (err == MCU_ERROR_OK);
}

bool crypto_hmac(const uint8_t* message, uint32_t message_size, key_handle_t* key, uint8_t* digest,
                 uint32_t digest_size, hash_alg_t alg) {
  ASSERT(message != NULL);
  ASSERT(key != NULL);
  ASSERT(digest != NULL);
  ASSERT(key->key.bytes != NULL);

  const mcu_err_t err = mcu_hash_hmac(crypto_alg_type(alg), message, message_size, key->key.bytes,
                                      key->key.size, digest, digest_size);
  return (err == MCU_ERROR_OK);
}

bool crypto_sha256_stream_init(void* ctx) {
  (void)ctx;

  const mcu_err_t err = mcu_hash_start(MCU_HASH_ALG_SHA256);
  return (err == MCU_ERROR_OK);
}

bool crypto_sha256_stream_update(void* ctx, uint8_t* buffer, uint32_t size) {
  (void)ctx;
  ASSERT(buffer != NULL);

  const mcu_err_t err = mcu_hash_update(buffer, size);
  return (err == MCU_ERROR_OK);
}

bool crypto_sha256_stream_final(void* ctx, uint8_t* digest_out) {
  (void)ctx;
  ASSERT(digest_out != NULL);

  const mcu_err_t err = mcu_hash_finish(digest_out, MCU_HASH_SHA256_DIGEST_LENGTH);
  return (err == MCU_ERROR_OK);
}
