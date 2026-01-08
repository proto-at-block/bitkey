/**
 * @file
 *
 * @brief Coprocessor Power Control
 *
 * @note For all APIs defined here, the coprocessor configuration determines
 * whether reset or power on/off is available.
 *
 * @{
 */

#pragma once

#include <stdbool.h>
#include <stdint.h>

/**
 * @brief Starts powering on the co-processor (non-blocking).
 *
 * @note Assumes active high.
 * @note Returns immediately. Use coproc_get_status() to poll for completion.
 */
void coproc_power_on(void);

/**
 * @brief Powers off the co-processor.
 *
 * @note Assumes active high.
 *
 * @note This method will not reset in the co-processor powering off if the
 * co-processor is asserting its own power.
 *
 * @note Blocks for the duration of power off.
 */
void coproc_power_off(void);

/**
 * @brief Resets the co-processor.
 *
 * @note Assumes active low.
 */
void coproc_power_reset(void);

/**
 * @brief Asserts the co-processor's reset line.
 *
 * @note Assumes active low.
 */
void coproc_power_assert_reset(void);

/**
 * @brief De-asserts the co-processor's reset line.
 *
 * @note Assumes active low.
 */
void coproc_power_deassert_reset(void);

/** @} */
