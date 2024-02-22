#pragma once

#include "mcu.h"
#include "mcu_gpio.h"

#include "em_cmu.h"
#include "em_usart.h"

#define MCU_USART0_CLOCK cmuClock_USART0
#define MCU_USART0       USART0

#define MCU_USART0_RX_INTERRUPT

typedef struct {
  mcu_gpio_config_t tx;
  mcu_gpio_config_t rx;
  uint32_t clock;
  uint32_t baudrate;
  struct {
    void* base;
    uint32_t index;
  } usart;
  bool rx_irq_timeout;
} mcu_usart_config_t;

#define MCU_USART_TX_BUFFER_LEN 1024
#define MCU_USART_RX_BUFFER_LEN 1024

void mcu_usart_init(mcu_usart_config_t* config);
uint32_t mcu_usart_read_timeout(uint8_t* data, uint32_t len, uint32_t timeout_ms);

/* mcu_usart_tx.c */
void mcu_usart_tx_init_dma(mcu_usart_config_t* config);
uint32_t mcu_usart_tx_write(void* usart, const uint8_t* data, uint32_t len);
void tx_ldma_schedule(void);

/* mcu_usart_rx.c */
void mcu_usart_rx_init_dma(mcu_usart_config_t* config);
uint32_t mcu_usart_rx_available(void);
