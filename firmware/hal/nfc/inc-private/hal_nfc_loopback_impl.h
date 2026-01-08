/**
 * @file
 *
 * @brief NFC Loopback Testing
 *
 * @{
 */

#pragma once

#include "hal_nfc.h"
#include "platform.h"

#include <stdbool.h>
#include <stdint.h>

#if defined(PLATFORM_CFG_NFC_READER_MODE) && (PLATFORM_CFG_NFC_READER_MODE)

/**
 * @brief Number of expected cards during loopback testing.
 */
#define HAL_NFC_LOOPBACK_EXPECTED_CARDS (1u)

/**
 * @brief Minimum number of milliseconds to sleep between polling retries.
 */
#define HAL_NFC_LOOPBACK_MIN_RETRY_SLEEP_MS (3u)

/**
 * @brief Maximum number of retries for anti-collision detection.
 */
#define HAL_NFC_LOOPBACK_MAX_RETRIES (3u)

/**
 * @brief Initialize the RFAL for loopback testing.
 *
 * @param mode  Loopback NFC mode.
 */
void hal_nfc_loopback_init(hal_nfc_mode_t mode);

/**
 * @brief Turns off the RF field.
 */
void hal_nfc_loopback_deinit(void);

/**
 * @brief Runs a step of the NFC loopback loop.
 *
 * @param callback Unused.
 */
void hal_nfc_loopback_run(hal_nfc_callback_t callback);

/**
 * @brief Performs an NFC loopback test.
 *
 * @param mode        The NFC reader mode to use.
 * @param timeout_ms  Timeout (milliseconds) for card detection.
 *
 * @return `true` if card detected, otherwise `false`.
 */
bool hal_nfc_loopback_test(hal_nfc_mode_t mode, uint32_t timeout_ms);

#endif

/** @} */
