/**
 * @file hal_nfc_loopback.h
 *
 * @brief NFC Loopback Testing
 *
 * @{
 */

#pragma once

#include "hal_nfc.h"

#include <stdbool.h>
#include <stdint.h>

/**
 * @brief Starts an NFC loopback test.
 *
 * @param mode        The NFC reader mode to use.
 * @param timeout_ms  Timeout (milliseconds) for card detection.
 */
void hal_nfc_loopback_test_start(hal_nfc_mode_t mode, uint32_t timeout_ms);

/**
 * @brief Returns `true` if a loopback test was run and succeeded.
 *
 * @return `true` if last loopback test passed, otherwise `false`.
 *
 * @note This method will only return `true` once after a loopback test has
 * passed. The caller is expected to cache the result if they need it to
 * persist. Starting another test afterwards will have this method then
 * reflect the status of the last test.
 */
bool hal_nfc_loopback_test_passed(void);

/** @} */
