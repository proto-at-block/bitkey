#include "secure_rng.h"

#include "assert.h"
#include "secure_engine.h"

#include <string.h>

bool crypto_random(uint8_t* data, uint32_t num_bytes) {
  if (!data || num_bytes == 0)
    return false;

  sl_se_command_context_t cmd_ctx = {0};
  sl_status_t status = sl_se_init_command_context(&cmd_ctx);
  if (status != SL_STATUS_OK) {
    return false;
  }
  if (sl_se_get_random(&cmd_ctx, data, num_bytes) != SL_STATUS_OK) {
    return false;
  }

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
