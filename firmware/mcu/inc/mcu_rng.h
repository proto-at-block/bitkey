/**
 * @file
 *
 * @brief MCU Random Number Generator (RNG)
 *
 * @{
 */

#pragma once

#include <stdint.h>

/**
 * @brief Initializes the RNG module.
 */
void mcu_rng_init(void);

/**
 * @brief Retrieves a random 32-bit integer from the RNG module.
 *
 * @return 32-bit random integer.
 *
 * @note This function will block a number of clock cycles as necessary to
 * generate a new random number.
 */
uint32_t mcu_rng_get(void);

/** @} */
