/**
 * @file
 *
 * @brief Near-Field Communication (NFC) Hardware Abstraction Layer (HAL).
 *
 * @{
 */

#pragma once

#include "mcu.h"
#include "mcu_i2c.h"
#include "rtos.h"

#include <stdbool.h>
#include <stdint.h>

/**
 * @brief Maximum number of active NFC timers.
 */
#define HAL_NFC_MAX_TIMERS (10U)

/**
 * @brief NFC active modes.
 */
typedef enum {
  /**
   * @brief Unused.
   */
  HAL_NFC_MODE_NONE,

  /**
   * @brief Listener mode.
   */
  HAL_NFC_MODE_LISTENER,

  /**
   * @brief Reader mode.
   */
  HAL_NFC_MODE_READER,

  /**
   * @brief Loopback mode for testing NFC type-A.
   */
  HAL_NFC_MODE_LOOPBACK_A,

  /**
   * @brief Loopback mode for testing NFC type-B.
   */
  HAL_NFC_MODE_LOOPBACK_B,
} hal_nfc_mode_t;

typedef struct {
  /**
   * @brief RFAL I2C instance.
   */
  struct {
    mcu_i2c_bus_config_t* bus;
    mcu_i2c_device_t* device;
  } i2c;

  /**
   * @brief GPIO used by the NFC stack to signal an NFC interrupt.
   */
  mcu_gpio_config_t irq;

  /**
   * @brief Timeout (in milliseconds) to block on I2C send or receive.
   */
  uint32_t transfer_timeout_ms;
} nfc_config_t;

/**
 * @brief Callback invoked by the NFC worker to transmit and receive data.
 *
 * @param[in]  rx        Received data from the last NFC transaction.
 * @param[in]  rx_len    Length of @p rx in bytes.
 * @param[out] tx        Data to transmit in the next NFC transaction.
 * @param[out] tx_len    Number of bytes written to @p tx.
 *
 * @return `true` if NFC data exchange should take place, otherwise `false`.
 *
 * @note @p tx_len is initially the length of @p tx. The caller should ensure
 * they write no more than @p tx_len bytes to @p tx.
 */
typedef bool (*hal_nfc_callback_t)(uint8_t* rx, uint32_t rx_len, uint8_t* tx, uint32_t* tx_len);

/**
 * @brief Initializes the NFC HAL.
 *
 * @param mode           Initial mode for the NFC controller (e.g. #HAL_NFC_MODE_LISTENER).
 * @param timer_callback Callback to invoke on NFC timer expiry.
 */
void hal_nfc_init(hal_nfc_mode_t mode, rtos_timer_callback_t timer_callback);

/**
 * @brief Re-configures the RFAL for the specified NFC mode.
 *
 * @param mode  NFC mode.
 */
void hal_nfc_set_mode(hal_nfc_mode_t mode);

/**
 * @brief Retrieves the current NFC mode.
 *
 * @return Current NFC mode.
 */
hal_nfc_mode_t hal_nfc_get_mode(void);

/**
 * @brief Reads interrupt registers from the NFC controller over I2C.
 */
void hal_nfc_wfi(void);

/**
 * @brief Handles pending NFC interrupts.
 */
void hal_nfc_handle_interrupts(void);

/**
 * @brief Runs a loop of the NFC worker.
 *
 * @param callback Callback to invoke on receipt of data / to get data to send.
 */
void hal_nfc_worker(hal_nfc_callback_t callback);

/** @} */
