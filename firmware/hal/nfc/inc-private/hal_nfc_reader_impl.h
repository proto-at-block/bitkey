/**
 * @file
 *
 * @brief NFC Reader
 *
 * @{
 */

#pragma once

#include "hal_nfc.h"
#include "platform.h"

#if defined(PLATFORM_CFG_NFC_READER_MODE) && (PLATFORM_CFG_NFC_READER_MODE)

/**
 * @brief Initialize the RFAL for active NFC read/write.
 */
void hal_nfc_reader_init(void);

/**
 * @brief Tears down the NFC reader.
 */
void hal_nfc_reader_deinit(void);

/**
 * @brief Runs a step of the NFC reader loop.
 *
 * @param callback Callback to invoke to retrieve data to send and/or pass data
 *                 that was received.
 */
void hal_nfc_reader_run(hal_nfc_callback_t callback);

#endif

/** @} */
