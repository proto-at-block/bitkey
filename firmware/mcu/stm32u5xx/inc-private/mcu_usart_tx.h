#pragma once

#include "mcu_usart.h"
#include "ringbuf.h"
#include "rtos_event_groups.h"
#include "rtos_mutex.h"
#include "rtos_semaphore.h"
#include "stm32u5xx.h"
#include "stm32u5xx_ll_dma.h"

typedef struct {
  /* Ring buffer for TX data */
  ringbuf_t ringbuf;
  uint8_t buffer[MCU_USART_TX_BUFFER_LEN];

  /* DMA transfer tracking */
  uint32_t xfer_len;    /* Current transfer length, 0 = inactive */
  uint32_t dma_channel; /* Allocated DMA channel number */
  uint32_t dma_request; /* DMA request line */

  /* USART instance */
  USART_TypeDef* usart;

  /* RTOS synchronization */
  rtos_mutex_t access;
  rtos_mutex_t rb_access; /* Separate mutex for ringbuffer */

  /* Status */
  bool initialized;
} mcu_usart_tx_state_t;

void mcu_usart_tx_init_dma(mcu_usart_tx_state_t* state, mcu_usart_config_t* config);
uint32_t mcu_usart_tx_write(mcu_usart_tx_state_t* state, const uint8_t* data, uint32_t len);
