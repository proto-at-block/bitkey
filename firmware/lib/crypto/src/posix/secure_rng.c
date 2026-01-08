#include "secure_rng.h"

#include "assert.h"
#include "crypto_impl.h"

#include <openssl/rand.h>

#include <stdint.h>

bool crypto_random(uint8_t* data, uint32_t num_bytes) {
  return (RAND_bytes(data, num_bytes) == OPENSSL_OK);
}

uint16_t crypto_rand_bounded_ticks(uint8_t bits) {
  // Clamp
  bits = bits > 16 ? 16 : bits;

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
