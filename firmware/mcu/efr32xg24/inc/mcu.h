#pragma once

#include <stdbool.h>
#include <stdint.h>
// clang-format off
#include "em_device.h"
#include "core_cm33.h"
// clang-format on

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
} mcu_err_t;

void mcu_init(void);
