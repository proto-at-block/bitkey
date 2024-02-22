#pragma once

#include <stdbool.h>
#include <stdint.h>

bool crypto_random(uint8_t* data, uint32_t num_bytes);

// Gets a random number with the specified number of bits up to 16 bits.
// Any `bits` value above 16 will be clamped to 16.
uint16_t crypto_rand_bounded_ticks(uint8_t bits);

// Get a 16-bit random number
uint16_t crypto_rand_short(void);
