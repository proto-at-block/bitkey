#pragma once

#include "mcu.h"
#include "mcu_gpio.h"
#include "mcu_usart_impl.h"

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
uint32_t mcu_usart_read_timeout(mcu_usart_config_t* config, uint8_t* data, uint32_t len,
                                uint32_t timeout_ms);
uint32_t mcu_usart_write(mcu_usart_config_t* config, const uint8_t* data, uint32_t len);
