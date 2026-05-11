#pragma once

#include <stdbool.h>
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

/** @brief Infinite timeout value for sleep inhibit (never power off). */
#define SLEEP_INHIBIT_INFINITE (UINT32_MAX)

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
 * @brief Set extra timeout added while USB charger is connected.
 * @param extra_ms Additional time on top of base + inhibit (0 to disable).
 * @note Separate from inhibit — does not interfere with flow-specific inhibits.
 *       The extension is applied whenever the timer is started or refreshed.
 */
void sleep_set_charger_extension(uint32_t extra_ms);

/**
 * @brief Get the currently configured timeout (base + inhibit + charger extension).
 * @return Total timeout in milliseconds.
 */
uint32_t sleep_get_configured_timeout(void);

/**
 * @brief Start the power timer with an absolute timeout value.
 * @param timeout_ms Absolute timeout in milliseconds (bypasses base + inhibit calculation).
 * @note This is for debug/CLI use. Normal code should use sleep_start_power_timer() +
 * sleep_inhibit().
 */
void sleep_start_power_timer_with_timeout(uint32_t timeout_ms);

/**
 * @brief Latch the sleep subsystem into a "shutting down" state.
 *
 * Once latched, all subsequent start/refresh/inhibit/charger-extension calls
 * become no-ops so that the countdown timer cannot be re-armed by any event
 * after the shutdown sequence has begun. Also stops any currently armed
 * timer and clears the `timer_running` bookkeeping that the one-shot timer
 * callback otherwise leaves stale.
 *
 * Intended to be called from the power-timer callback and from any other
 * entry point that commits to powering the device off.
 *
 * @return true if this call transitioned into the shutdown state, false if
 *         it was already latched (i.e., shutdown is already in progress).
 *         Callers should treat `false` as "someone else already started the
 *         shutdown sequence; bail out of yours" to avoid double-issuing the
 *         shutdown IPCs/UI events.
 */
bool sleep_begin_shutdown(void);

/**
 * @brief Query whether the shutdown latch has been set.
 */
bool sleep_is_shutting_down(void);

/**
 * @brief Clear the shutdown latch set by `sleep_begin_shutdown`.
 *
 * Used when a shutdown sequence determines mid-flow that it shouldn't
 * proceed — e.g., the UXC reboots during the USB-plugged power-off polling
 * loop and sysinfo bails so it can rekey the secure channel. Without this,
 * the latch would remain set indefinitely and the sleep timer could never
 * be re-engaged.
 *
 * The caller is responsible for restarting the sleep timer if appropriate;
 * this only clears the latch state.
 */
void sleep_cancel_shutdown(void);
