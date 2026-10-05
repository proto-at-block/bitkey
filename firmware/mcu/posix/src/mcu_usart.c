#include "mcu_usart.h"

void mcu_usart_init(mcu_usart_config_t* config) {
  (void)config;
}

__attribute__((weak)) uint32_t mcu_usart_read_timeout(mcu_usart_config_t* config, uint8_t* data,
                                                      uint32_t len, uint32_t timeout_ms) {
  (void)config;
  (void)data;
  (void)len;
  (void)timeout_ms;
  return 0;
}

__attribute__((weak)) uint32_t mcu_usart_write(mcu_usart_config_t* config, const uint8_t* data,
                                               uint32_t len) {
  (void)config;
  (void)data;
  return len;
}
