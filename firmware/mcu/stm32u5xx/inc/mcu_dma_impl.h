#pragma once

#include "mcu_dma.h"
#include "stm32u5xx.h"
#include "stm32u5xx_ll_bus.h"
#include "stm32u5xx_ll_dma.h"

#include <stdbool.h>
#include <stdint.h>

// Maximum length of one DMA transfer for STM32U5
#define MCU_DMA_MAX_XFER_COUNT 65535

// STM32U5 DMA signal definitions would go here if needed
enum mcu_dma_signal_t {
  MCU_DMA_SIGNAL_NONE = 0,
  MCU_DMA_SIGNAL_USART1_RX = LL_GPDMA1_REQUEST_USART1_RX,
  MCU_DMA_SIGNAL_USART1_TX = LL_GPDMA1_REQUEST_USART1_TX,
  MCU_DMA_SIGNAL_USART2_RX = LL_GPDMA1_REQUEST_USART2_RX,
  MCU_DMA_SIGNAL_USART2_TX = LL_GPDMA1_REQUEST_USART2_TX,
  MCU_DMA_SIGNAL_USART3_RX = LL_GPDMA1_REQUEST_USART3_RX,
  MCU_DMA_SIGNAL_USART3_TX = LL_GPDMA1_REQUEST_USART3_TX,
  MCU_DMA_SIGNAL_UART4_RX = LL_GPDMA1_REQUEST_UART4_RX,
  MCU_DMA_SIGNAL_UART4_TX = LL_GPDMA1_REQUEST_UART4_TX,
  MCU_DMA_SIGNAL_UART5_RX = LL_GPDMA1_REQUEST_UART5_RX,
  MCU_DMA_SIGNAL_UART5_TX = LL_GPDMA1_REQUEST_UART5_TX,
  MCU_DMA_SIGNAL_LPUART1_RX = LL_GPDMA1_REQUEST_LPUART1_RX,
  MCU_DMA_SIGNAL_LPUART1_TX = LL_GPDMA1_REQUEST_LPUART1_TX,
  MCU_DMA_SIGNAL_I2C1_RX = LL_GPDMA1_REQUEST_I2C1_RX,
  MCU_DMA_SIGNAL_I2C1_TX = LL_GPDMA1_REQUEST_I2C1_TX,
  MCU_DMA_SIGNAL_I2C2_RX = LL_GPDMA1_REQUEST_I2C2_RX,
  MCU_DMA_SIGNAL_I2C2_TX = LL_GPDMA1_REQUEST_I2C2_TX,
  MCU_DMA_SIGNAL_OCTOSPI1 = LL_GPDMA1_REQUEST_OCTOSPI1,
  MCU_DMA_SIGNAL_OCTOSPI2 = LL_GPDMA1_REQUEST_OCTOSPI2,
};

/* STM32U5 has GPDMA1 with 16 channels and LPDMA1 with 4 channels */
#define MCU_DMA_MAX_CHANNELS 16

/* DMA channel to IRQ mapping */
typedef struct {
  IRQn_Type irqn;
  uint32_t channel_mask;
} mcu_dma_irq_map_t;

static const mcu_dma_irq_map_t dma_irq_map[] = {
  {GPDMA1_Channel0_IRQn, LL_DMA_CHANNEL_0},   {GPDMA1_Channel1_IRQn, LL_DMA_CHANNEL_1},
  {GPDMA1_Channel2_IRQn, LL_DMA_CHANNEL_2},   {GPDMA1_Channel3_IRQn, LL_DMA_CHANNEL_3},
  {GPDMA1_Channel4_IRQn, LL_DMA_CHANNEL_4},   {GPDMA1_Channel5_IRQn, LL_DMA_CHANNEL_5},
  {GPDMA1_Channel6_IRQn, LL_DMA_CHANNEL_6},   {GPDMA1_Channel7_IRQn, LL_DMA_CHANNEL_7},
  {GPDMA1_Channel8_IRQn, LL_DMA_CHANNEL_8},   {GPDMA1_Channel9_IRQn, LL_DMA_CHANNEL_9},
  {GPDMA1_Channel10_IRQn, LL_DMA_CHANNEL_10}, {GPDMA1_Channel11_IRQn, LL_DMA_CHANNEL_11},
  {GPDMA1_Channel12_IRQn, LL_DMA_CHANNEL_12}, {GPDMA1_Channel13_IRQn, LL_DMA_CHANNEL_13},
  {GPDMA1_Channel14_IRQn, LL_DMA_CHANNEL_14}, {GPDMA1_Channel15_IRQn, LL_DMA_CHANNEL_15}};

typedef enum { MCU_DMA_MODE_BASIC, MCU_DMA_MODE_CIRCULAR } mcu_dma_mode_t;

typedef enum {
  MCU_DMA_DIR_M2P,  // Memory to Peripheral
  MCU_DMA_DIR_P2M,  // Peripheral to Memory
  MCU_DMA_DIR_M2M   // Memory to Memory
} mcu_dma_direction_t;

typedef enum {
  MCU_DMA_REQ_PRIORITY_LOWEST = LL_DMA_LOW_PRIORITY_LOW_WEIGHT,
  MCU_DMA_REQ_PRIORITY_LOW = LL_DMA_LOW_PRIORITY_MID_WEIGHT,
  MCU_DMA_REQ_PRIORITY_MEDIUM = LL_DMA_LOW_PRIORITY_HIGH_WEIGHT,
  MCU_DMA_REQ_PRIORITY_HIGH = LL_DMA_HIGH_PRIORITY,
} mcu_dma_req_prio_t;

typedef struct {
  void* src_addr;
  void* dst_addr;
  uint32_t length;
  mcu_dma_direction_t direction;
  mcu_dma_data_size_t src_width;
  mcu_dma_data_size_t dst_width;
  bool src_increment;
  bool dst_increment;
  uint32_t request;  // DMA request line (e.g., USART1_TX)
  mcu_dma_mode_t mode;
  mcu_dma_req_prio_t priority;
  mcu_dma_callback_t callback;
  void* xfer_node;
  void* user_param;
} mcu_dma_config_t;

// STM32 DMA functions
mcu_err_t mcu_dma_init(const int8_t nvic_priority);
mcu_err_t mcu_dma_channel_free(uint32_t channel);
mcu_err_t mcu_dma_channel_configure(uint32_t channel, const mcu_dma_config_t* config);
mcu_err_t mcu_dma_channel_start(uint32_t channel);
mcu_err_t mcu_dma_channel_stop(uint32_t channel);
mcu_err_t mcu_dma_channel_is_active(uint32_t channel, bool* active);

/**
 * @brief Retrieves the number of bytes remaining for the current transfer in a
 * linked list DMA transfer.
 *
 * @param[in]  channel    Allocated DMA channel.
 * @param[out] remaining  Output pointer to store the number of bytes remaining
 *                        to be transferred.
 *
 * @return `MCU_ERROR_OK` on success, otherwise an error code as defined in
 * `mcu_err_t`.
 *
 * @note This function should only be used for DMA channels that were configured
 * in mode #MCU_DMA_MODE_CIRCULAR, otherwise @p remaining will always be `0`.
 * To compute the number of bytes remaining for a channel configured in another
 * mode, use #mcu_dma_channel_get_src_address() or #mcu_dma_channel_get_dest_address()
 * and take the difference against the source or destination address the transfer
 * was started with.
 */
mcu_err_t mcu_dma_channel_get_remaining(uint32_t channel, uint32_t* remaining);

/**
 * @brief Retrieves the current source address for the transfer being executed
 * on the given @p channel.
 *
 * @details If the current transfer is set to increment the source address on
 * arbritration, then the returned pointer will be offset by the number of
 * arbtritrations that have occurred multiplied by the source address
 * increment size.
 *
 * @param[in]  channel  Allocated DMA channel.
 * @param[out] addr     Output pointer to store the current source address.
 *
 * @return `MCU_ERROR_OK` on success, otherwise an error code as defined in
 * `mcu_err_t`.
 */
mcu_err_t mcu_dma_channel_get_src_address(uint32_t channel, uintptr_t* addr);

/**
 * @brief Retrieves the current destination address for the transfer being
 * executed on the given @p channel.
 *
 * @details If the current transfer is set to increment the destination address
 * on arbritration, then the returned pointer will be offset by the number of
 * arbtritrations that have occurred multiplied by the destination address
 * increment size.
 *
 * @param[in]  channel  Allocated DMA channel.
 * @param[out] addr     Output pointer to store the current destination
 *                      address.
 *
 * @return `MCU_ERROR_OK` on success, otherwise an error code as defined in
 * `mcu_err_t`.
 */
mcu_err_t mcu_dma_channel_get_dest_address(uint32_t channel, uintptr_t* addr);

mcu_err_t mcu_dma_channel_update_addresses(uint32_t channel, void* src_addr, void* dst_addr,
                                           uint32_t length);
