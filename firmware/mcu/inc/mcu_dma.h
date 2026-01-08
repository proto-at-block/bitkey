#pragma once

#include "mcu.h"

#include <stdbool.h>
#include <stdint.h>

typedef enum {
  MCU_DMA_SIZE_1_BYTE = 0,   ///< Byte
  MCU_DMA_SIZE_2_BYTES = 1,  ///< Halfword
  MCU_DMA_SIZE_4_BYTES = 2   ///< Word
} mcu_dma_data_size_t;

typedef int mcu_dma_signal_t;

#define MCU_DMA_IRQ_PRIORITY 4

// DMA interrupt flags
#define MCU_DMA_FLAG_HALF_TRANSFER     0x01
#define MCU_DMA_FLAG_TRANSFER_COMPLETE 0x02
#define MCU_DMA_FLAG_TRANSFER_ERROR    0x04

typedef bool (*mcu_dma_callback_t)(uint32_t channel, uint32_t sequence_num, void* user_param);

mcu_err_t mcu_dma_init(const int8_t nvic_priority);
uint32_t mcu_dma_get_max_xfer_size(void);
mcu_err_t mcu_dma_allocate_channel(uint32_t* channel);
mcu_err_t mcu_dma_peripheral_memory(uint32_t channel, mcu_dma_signal_t signal, void* dst, void* src,
                                    bool dst_inc, int len, mcu_dma_data_size_t size,
                                    mcu_dma_callback_t callback, void* user_param);
mcu_err_t mcu_dma_memory_peripheral(int32_t channel, mcu_dma_signal_t signal, void* dst, void* src,
                                    bool src_inc, int len, mcu_dma_data_size_t size,
                                    mcu_dma_callback_t callback, void* user_param);
