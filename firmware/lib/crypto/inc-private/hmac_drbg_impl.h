#pragma once

#include "hash.h"
#include "secutils.h"

#include <stdint.h>

typedef struct {
  uint8_t v[SHA256_DIGEST_SIZE];
  uint8_t k[SHA256_DIGEST_SIZE];
  secure_bool_t initialized;
} hmac_drbg_state_t;

/* Initialize an hmac_drbg_state using an entropy source.
 *
 * Implementation note: This function will accept at most 64 bytes of entropy.
 */
void crypto_hmac_drbg_init(const uint8_t* entropy, size_t entropy_length, hmac_drbg_state_t* state);

/* Reseed an hmac_drbg_state using an entropy source.
 * This will combine new entropy with the existing state.
 *
 * Implementation note: This function will accept at most 64 bytes of entropy per call.
 */
void crypto_hmac_drbg_reseed(const uint8_t* entropy, size_t entropy_length,
                             hmac_drbg_state_t* state);

// Generate pseudorandom output using an initialized hmac_drbg_state.
void crypto_hmac_drbg_generate(hmac_drbg_state_t* state, uint8_t* output,
                               size_t output_size_in_bytes);
