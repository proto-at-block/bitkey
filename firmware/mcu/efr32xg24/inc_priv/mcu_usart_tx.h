#pragma once

#include <mcu_usart.h>

mcu_err_t mcu_usart_tx_init_dma(mcu_usart_config_t* config);
uint32_t mcu_usart_tx_write(mcu_usart_config_t* config, const uint8_t* data, uint32_t len);
