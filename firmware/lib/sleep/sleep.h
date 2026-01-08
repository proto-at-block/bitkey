#pragma once

#include <stdint.h>

/**
 * @file sleep.h
 * @brief Power timer management for device sleep/power-off.
 *
 * The power timer controls how long the device stays on when locked.
 * When the timer expires, the device powers off.
 *
 * Timer lifecycle:
 * - Unlocked: Power timer stopped (auth timer running instead)
 * - Locked: Power timer running (60s default, extendable via inhibit)
 *
 * The inhibit mechanism allows temporarily extending the timeout
 * (e.g., during PIN delay periods to keep the device awake).
 */

/** @brief Base power timeout when locked (ms). */
#define POWER_TIMEOUT_MS (60000)

/** @brief Callback invoked when the power timer expires. */
typedef void (*sleep_timer_callback_t)(void*);

/**
 * @brief Initialize the sleep subsystem.
 * @param callback Function called when power timer expires (triggers power-off).
 * @note Does not start the timer. Call sleep_start_power_timer() to begin countdown.
 */
void sleep_init(sleep_timer_callback_t callback);

/**
 * @brief Start the power-off countdown timer.
 * @note Called when device transitions to locked state.
 */
void sleep_start_power_timer(void);

/**
 * @brief Stop the power-off countdown timer.
 * @note Called when device transitions to unlocked state. Also clears any inhibit.
 */
void sleep_stop_power_timer(void);

/**
 * @brief Refresh the power timer if running.
 * @note Restarts the countdown from current time. No-op if timer not running.
 */
void sleep_refresh_power_timer(void);

/**
 * @brief Extend the power timeout temporarily.
 * @param additional_ms Extra time to add to base timeout (overwrites previous inhibit).
 * @note If timer is running, restarts it from current time with new extended duration.
 */
void sleep_inhibit(uint32_t additional_ms);

/**
 * @brief Clear any active inhibit, restoring base timeout.
 * @note If timer is running, restarts it from current time with base duration.
 */
void sleep_clear_inhibit(void);

/**
 * @brief Get the currently configured timeout (base + inhibit).
 * @return Total timeout in milliseconds.
 */
uint32_t sleep_get_configured_timeout(void);
