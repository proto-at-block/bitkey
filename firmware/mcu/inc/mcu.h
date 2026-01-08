#pragma once

#include "mcu_impl.h"

#include <stdbool.h>
#include <stdint.h>

typedef enum {
  MCU_ERROR_UNKNOWN = 0,
  MCU_ERROR_OK = 1,
  MCU_ERROR_NOT_IMPLEMENTED,
  MCU_ERROR_PARAMETER,
  MCU_ERROR_ILLEGAL_HANDLE,
  MCU_ERROR_DMA_ALLOC,
  MCU_ERROR_SPI_MODE,
  MCU_ERROR_SPI_BUSY,
  MCU_ERROR_ALREADY_INITIALISED,
  MCU_ERROR_NOT_INITIALISED,
  MCU_ERROR_DMA_CHANNELS_EXHAUSTED,
  MCU_ERROR_DMA_CHANNEL_NOT_ALLOC,
  MCU_ERROR_OUT_OF_MEM,

  /**
   * @brief Operation failed due to timeout.
   */
  MCU_ERROR_TIMEOUT,

  /**
   * @brief Call failed because another thread owns exclusive access to the
   * hardware module, or the current thread has not acquired exclusive access
   * of the hardware module.
   */
  MCU_ERROR_OWNER,

  /**
   * @brief Invalid key passed to a PKA operation.
   */
  MCU_ERROR_PKA_INVALID_KEY,

  /**
   * @brief Invalid signature passed to a PKA operation.
   */
  MCU_ERROR_PKA_INVALID_SIGNATURE,

  /**
   * @brief PKA operation failed.
   */
  MCU_ERROR_PKA_FAIL,

  /**
   * @brief Authentication failed during crypto operation.
   */
  MCU_ERROR_AUTH_FAILED
} mcu_err_t;

void mcu_init(void);
