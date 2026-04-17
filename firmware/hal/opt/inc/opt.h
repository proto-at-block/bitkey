#pragma once

#include <stdbool.h>

/**
 * @file
 *
 * @brief Option-bytes HAL public API.
 */

/**
 * @brief Attempts to set the device production lock state.
 *
 * @return `true` when production lock is successfully set (or already set),
 * otherwise `false`.
 */
bool opt_device_set_production(void);

/**
 * @brief Reads whether the device is currently production locked.
 *
 * @return `true` when production lock is set, otherwise `false`.
 */
bool opt_device_is_production(void);
