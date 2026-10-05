#include "serial.h"

#include "mcu.h"
#include "mcu_usart.h"
#include "printf.h"
#include "wstring.h"

#include <stdbool.h>

extern serial_config_t serial_config;

static uint8_t buffer[MCU_USART_RX_BUFFER_LEN] = {0};

void _putchar(char c);

void serial_init(void) {
  mcu_usart_init(&serial_config.usart);
}

void serial_echo(void) {
  const uint32_t max_read = sizeof(buffer) - 1;
  const uint32_t n_read = mcu_usart_read_timeout(&serial_config.usart, buffer, max_read, 100);
  const uint32_t terminator_index = n_read < max_read ? n_read : max_read;
  buffer[terminator_index] = '\0';

  if (n_read > 0) {
    printf("%s", buffer);
  }

  memzero(buffer, sizeof(buffer));
}

void _putchar(char c) {
  if (serial_config.retarget_printf.enable) {
    mcu_usart_write(&serial_config.usart, (uint8_t*)&c, 1);
  }
}
