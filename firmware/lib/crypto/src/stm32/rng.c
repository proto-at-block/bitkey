#include "assert.h"
#include "hmac_drbg_impl.h"
#include "mcu_rng.h"
#include "rtos_mutex.h"
#include "secure_rng.h"
#include "secutils.h"
#include "wstring.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>

static hmac_drbg_state_t drbg_state = {
  .initialized = SECURE_FALSE,
};

#ifndef IMAGE_TYPE_BOOTLOADER
static rtos_mutex_t rng_mutex;
#endif

static bool trng_random(uint8_t* data, uint32_t num_bytes) {
  if ((data == NULL) || (num_bytes == 0)) {
    return false;
  }

  while (num_bytes) {
    const uint32_t rand_word = mcu_rng_get();
    const size_t size = (num_bytes > sizeof(rand_word) ? sizeof(rand_word) : num_bytes);
    memcpy(data, (const uint8_t*)&rand_word, size);
    data += size;
    num_bytes -= size;
  }

  return true;
}

void crypto_random_init() {
  static bool initialized = false;
  if (!initialized) {
#ifndef IMAGE_TYPE_BOOTLOADER
    rtos_mutex_create(&rng_mutex);
#endif
    // This is twice as much entropy as needed as a guard against a biased TRNG
    uint8_t entropy[2 * SHA256_DIGEST_SIZE];
    ASSERT(trng_random(entropy, sizeof(entropy)));
    crypto_hmac_drbg_init(entropy, sizeof(entropy), &drbg_state);
    memzero(entropy, sizeof(entropy));
    initialized = true;
  }
}

bool crypto_random(uint8_t* data, uint32_t num_bytes) {
#ifndef IMAGE_TYPE_BOOTLOADER
  rtos_mutex_lock(&rng_mutex);
#endif
  crypto_hmac_drbg_generate(&drbg_state, data, num_bytes);
#ifndef IMAGE_TYPE_BOOTLOADER
  rtos_mutex_unlock(&rng_mutex);
#endif
  return true;
}

uint16_t crypto_rand_bounded_ticks(uint8_t bits) {
  // Clamp
  bits = bits > 16 ? 16 : bits;

  // Bitmasks are always one less than the power of two.
  uint16_t bitmask = (1 << bits) - 1;

  uint16_t ticks = 0;
  ASSERT(crypto_random((uint8_t*)&ticks, sizeof(uint16_t)));
  return (ticks & bitmask);
}

uint16_t crypto_rand_short(void) {
  uint16_t ticks = 0;
  ASSERT(crypto_random((uint8_t*)&ticks, sizeof(uint16_t)));
  return ticks;
}
