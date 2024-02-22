#pragma once

#include "secure_rng.h"

// Experimental library for fault testing.

// Simulate a fault.
//
// Action 0) Branch forward `n` bytes. This *somewhat* simulates a fault that skips over
//           instructions.
// Action 1) Pick a random register and flip a bit.
#ifdef CONFIG_PROD
// Guard against accidentally including this in customer builds.
#define SIMULATE_FAULT(n)
#else
#define SIMULATE_FAULT(n)                            \
  do {                                               \
    const uint16_t num_actions = 2;                  \
    uint16_t rand_num = crypto_rand_short();         \
    uint16_t action = rand_num % num_actions;        \
    switch (action) {                                \
      case 0:                                        \
        __asm__ volatile("b " #n "\n\t");            \
        break;                                       \
      case 1: {                                      \
        int rand_reg = rand_num % 31;                \
        int rand_bit = 1 << (rand_num % 64);         \
        volatile int* register_ptr = (int*)rand_reg; \
        *register_ptr ^= rand_bit;                   \
        break;                                       \
      }                                              \
    }                                                \
  } while (0)
#endif
