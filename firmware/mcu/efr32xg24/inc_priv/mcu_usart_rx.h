#pragma once

#include <mcu_usart.h>

mcu_err_t mcu_usart_rx_init_dma(mcu_usart_config_t* config);
uint32_t mcu_usart_rx_available(mcu_usart_config_t* config);
uint32_t mcu_usart_rx_read(mcu_usart_config_t* config, uint8_t* data, uint32_t len,
                           uint32_t timeout_ms);
