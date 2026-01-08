/**
 * @file
 *
 * @brief NFC Listener
 *
 * @{
 */

#pragma once

#include "hal_nfc.h"

/**
 * @brief Initialize the RFAL for passive NFC listening.
 */
void hal_nfc_listener_init(void);

/**
 * @brief Tears down the active NFC listener.
 */
void hal_nfc_listener_deinit(void);

/**
 * @brief Runs a step of the NFC listener loop.
 *
 * @param callback Callback to invoke to retrieve data to send and/or pass data
 *                 that was received.
 */
void hal_nfc_listener_run(hal_nfc_callback_t callback);

/** @} */
