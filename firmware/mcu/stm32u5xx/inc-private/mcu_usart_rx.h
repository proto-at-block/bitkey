#pragma once

#include "mcu_usart.h"
#include "ringbuf.h"
#include "rtos_event_groups.h"
#include "rtos_mutex.h"
#include "rtos_semaphore.h"
#include "stm32u5xx.h"
#include "stm32u5xx_ll_dma.h"

typedef struct {
  /* Circular Buffer */
  uint8_t buffer[MCU_USART_RX_BUFFER_LEN];
  uint32_t rd; /* Next byte to read out of the buffer */
  uint32_t wr; /* Next buffer write address */

  /* DMA channel */
  struct {
    uint32_t channel_num;        /* DMA channel number */
    LL_DMA_LinkNodeTypeDef node; /* DMA linked list node for circular transfers */
  } dma;

  /* USART instance */
  USART_TypeDef* usart;

  /* RTOS synchronization */
  rtos_mutex_t access;
  rtos_mutex_t rb_access;
  rtos_semaphore_t timeout;
  rtos_event_group_t events;

  /* Status */
  bool initialized;
} mcu_usart_rx_state_t;

void mcu_usart_rx_init_dma(mcu_usart_rx_state_t* state, mcu_usart_config_t* config);
uint32_t mcu_usart_rx_read(mcu_usart_rx_state_t* state, uint8_t* data, uint32_t len,
                           uint32_t timeout_ms);
uint32_t mcu_usart_rx_available(mcu_usart_rx_state_t* state);
void mcu_usart_rx_dma_irq_handler(void);
void mcu_usart_rx_idle_irq_handler(mcu_usart_rx_state_t* state);
