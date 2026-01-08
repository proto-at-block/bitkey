/**
 * @file
 *
 * @{
 */

#pragma once

#include "exti.h"
#include "hal_nfc.h"
#include "rfal_nfc.h"
#include "rtos.h"

#include <stdbool.h>
#include <stdint.h>

/**
 * @brief Timeout (ms) for NFC operations while operating in listener mode.
 */
#define HAL_NFC_DEFAULT_LISTENER_TIMEOUT_MS (1000U)

/**
 * @brief Timeout (ms) for NFC operations while operating in reader or loopback
 * mode.
 */
#define HAL_NFC_DEFAULT_READER_TIMEOUT_MS (5000U)

/**
 * @brief Enable/disabling logging in the NFC HAL.
 */
#define HAL_NFC_LOG_COMMS (0)

/**
 * @brief Event bitmask posted to the NFC event group.
 */
typedef enum {
  /**
   * @brief Card was detected.
   */
  HAL_NFC_EVENT_CARD_DETECTED = (1u << 0),

  /**
   * @brief Time out for card detection.
   */
  HAL_NFC_EVENT_CARD_TIMEOUT = (1u << 1),
} hal_nfc_event_t;

/**
 * @brief Internal HAL state.
 */
typedef struct {
  /**
   * @brief NFC discovery configuration.
   */
  rfalNfcDiscoverParam discovery_cfg;

  /**
   * @brief `true` if an I2C transfer to the radio is taking place.
   */
  bool transfer_in_progress;

  /**
   * @brief Number of milliseconds to wait for an I2C operation to finish.
   */
  uint32_t transfer_timeout_ms;

  /**
   * @brief RF external interrupt configuration.
   */
  exti_config_t irq_cfg;

#if defined(PLATFORM_CFG_NFC_READER_MODE) && (PLATFORM_CFG_NFC_READER_MODE)
  /**
   * @brief Event group used for NFC operation signaling.
   */
  rtos_event_group_t nfc_events;

  /**
   * @brief Overriden timeout for NFC card detection.
   */
  uint32_t card_detection_timeout_ms;
#endif

  /**
   * @brief Current active NFC mode.
   *
   * @details When the NFC worker runs, this will be updated to `next_mode` if
   * they are not equal.
   */
  hal_nfc_mode_t current_mode;

  /**
   * @brief Previous NFC mode.
   *
   * @details When the current NFC mode changes, this is updated to its
   * previous value.
   */
  hal_nfc_mode_t prev_mode;

  /**
   * @brief Next NFC mode to enter.
   *
   * @details When the NFC worker runs, the current mode will be updated to the
   * next mode.
   */
  hal_nfc_mode_t next_mode;

  /**
   * @brief Pointer to the last buffer of received data over NFC.
   */
  uint8_t* rx_buf;

  /**
   * @brief Number of bytes written to #hal_nfc_priv_t.rx_buf.
   */
  uint16_t* rx_len;
} hal_nfc_priv_t;

/** @} */
