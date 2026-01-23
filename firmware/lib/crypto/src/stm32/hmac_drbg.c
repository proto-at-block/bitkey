
#include "assert.h"
#include "hash.h"
#include "hmac_drbg_impl.h"
#include "mcu.h"
#include "mcu_hash.h"
#include "wstring.h"

#include <stdint.h>
#include <string.h>

// This implementation of HMAC-DRBG follows NIST SP 800-90A Rev. 1 (section 10.1.2).
// Note that there is no reseed counter.
// The maxium reseed interval is 2^48 calls to crypto_hmac_drbg_generate.
// Since that cannot reasonably happen on this device it would effectively be dead code.

// Update function specified in section 10.1.2.2
static void update(const uint8_t* data, size_t data_length, hmac_drbg_state_t* state) {
  // Use data length <= SHA256_DIGEST_SIZE * 2 so we don't have to malloc here.
  // Should include a wrapper for incremental HMAC in the future.
  ASSERT(data_length <= SHA256_DIGEST_SIZE * 2);
  ASSERT(state != NULL);

  // Must be large enough to hold: v || 0x00 || data
  uint8_t temp[SHA256_DIGEST_SIZE * 3 + 1] = {0};
  memcpy(temp, state->v, SHA256_DIGEST_SIZE);

  if (data != NULL) {
    memcpy(temp + SHA256_DIGEST_SIZE + 1, data, data_length);
  } else {
    ASSERT(data_length == 0);
  }

  // k = HMAC(k, v || 0x00 || data)
  ASSERT(mcu_hash_hmac(MCU_HASH_ALG_SHA256, temp, SHA256_DIGEST_SIZE + 1 + data_length, state->k,
                       SHA256_DIGEST_SIZE, state->k, SHA256_DIGEST_SIZE) == MCU_ERROR_OK);
  // v = HMAC(k, v)
  ASSERT(mcu_hash_hmac(MCU_HASH_ALG_SHA256, state->v, SHA256_DIGEST_SIZE, state->k,
                       SHA256_DIGEST_SIZE, state->v, SHA256_DIGEST_SIZE) == MCU_ERROR_OK);

  if (data_length > 0) {
    memcpy(temp, state->v, SHA256_DIGEST_SIZE);
    temp[SHA256_DIGEST_SIZE] = 0x01;

    // k = HMAC(k, v || 0x01 || data)
    ASSERT(mcu_hash_hmac(MCU_HASH_ALG_SHA256, temp, SHA256_DIGEST_SIZE + 1 + data_length, state->k,
                         SHA256_DIGEST_SIZE, state->k, SHA256_DIGEST_SIZE) == MCU_ERROR_OK);
    // v = HMAC(k, v)
    ASSERT(mcu_hash_hmac(MCU_HASH_ALG_SHA256, state->v, SHA256_DIGEST_SIZE, state->k,
                         SHA256_DIGEST_SIZE, state->v, SHA256_DIGEST_SIZE) == MCU_ERROR_OK);
  }
  memzero(temp, sizeof(temp));
}

// Instantiate function specified in section 10.1.2.3
void crypto_hmac_drbg_init(const uint8_t* entropy, size_t entropy_length,
                           hmac_drbg_state_t* state) {
  // Use entropy_length <= SHA256_DIGEST_SIZE * 2 so we don't have to malloc.
  // Should include a wrapper for incremental HMAC in the future.
  ASSERT(entropy_length <= SHA256_DIGEST_SIZE * 2);
  ASSERT(entropy != NULL);
  ASSERT(state != NULL);

  memset(state->k, 0x00, SHA256_DIGEST_SIZE);
  memset(state->v, 0x01, SHA256_DIGEST_SIZE);
  state->initialized = SECURE_TRUE;

  crypto_hmac_drbg_reseed(entropy, entropy_length, state);
}

// Update function specified in section 10.1.2.4
void crypto_hmac_drbg_reseed(const uint8_t* entropy, size_t entropy_length,
                             hmac_drbg_state_t* state) {
  // Use entropy length <= SHA256_DIGEST_SIZE * 2 so we don't have to malloc.
  // Should include a wrapper for incremental HMAC in the future.
  ASSERT(entropy_length <= SHA256_DIGEST_SIZE * 2);
  ASSERT(entropy != NULL);
  ASSERT(state != NULL);
  ASSERT(state->initialized == SECURE_TRUE);

  update(entropy, entropy_length, state);
}

// Generate function specified in section 10.1.2.5
void crypto_hmac_drbg_generate(hmac_drbg_state_t* state, uint8_t* output,
                               size_t output_size_in_bytes) {
  ASSERT(state != NULL);
  ASSERT(output != NULL);
  ASSERT(state->initialized == SECURE_TRUE);

  const size_t num_blocks = output_size_in_bytes / SHA256_DIGEST_SIZE;
  for (size_t i = 0; i < num_blocks; i++) {
    // v = HMAC(k, v)
    ASSERT(mcu_hash_hmac(MCU_HASH_ALG_SHA256, state->v, SHA256_DIGEST_SIZE, state->k,
                         SHA256_DIGEST_SIZE, state->v, SHA256_DIGEST_SIZE) == MCU_ERROR_OK);
    memcpy(output, state->v, SHA256_DIGEST_SIZE);
    output += SHA256_DIGEST_SIZE;
  }

  const size_t remainder = output_size_in_bytes % SHA256_DIGEST_SIZE;
  if (remainder > 0) {
    // v = HMAC(k, v)
    ASSERT(mcu_hash_hmac(MCU_HASH_ALG_SHA256, state->v, SHA256_DIGEST_SIZE, state->k,
                         SHA256_DIGEST_SIZE, state->v, SHA256_DIGEST_SIZE) == MCU_ERROR_OK);
    memcpy(output, state->v, remainder);
  }

  update(NULL, 0, state);
}
