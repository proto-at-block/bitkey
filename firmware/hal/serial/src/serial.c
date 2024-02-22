#include "serial.h"

#include "mcu.h"
#include "mcu_usart.h"
#include "printf.h"

#include <stdbool.h>
#include <string.h>

extern serial_config_t serial_config;

static uint8_t buffer[MCU_USART_RX_BUFFER_LEN] = {0};

void _putchar(char c);

void serial_init(void) {
  mcu_usart_init(&serial_config.usart);
}

void serial_echo(void) {
  memset(buffer, 0, MCU_USART_RX_BUFFER_LEN);

  const uint32_t n_read = mcu_usart_read_timeout(buffer, MCU_USART_RX_BUFFER_LEN, 100);
  if (n_read > 0) {
    printf("%s", buffer);
  }
}

void _putchar(char c) {
  if (serial_config.retarget_printf.enable) {
    mcu_usart_tx_write(serial_config.retarget_printf.target, (uint8_t*)&c, 1);
  }
}
