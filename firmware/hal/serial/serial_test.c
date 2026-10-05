#include "serial.h"

#include <criterion/criterion.h>

#include <string.h>

serial_config_t serial_config = {
  .retarget_printf =
    {
      .enable = true,
    },
};

static uint8_t received[MCU_USART_RX_BUFFER_LEN];
static uint8_t echoed[MCU_USART_RX_BUFFER_LEN];
static uint8_t* read_buffer;
static size_t echoed_len;

uint32_t mcu_usart_read_timeout(mcu_usart_config_t* config, uint8_t* data, uint32_t len,
                                uint32_t timeout_ms) {
  (void)config;
  (void)timeout_ms;

  cr_assert_eq(len, MCU_USART_RX_BUFFER_LEN - 1);
  read_buffer = data;
  memcpy(data, received, len);
  return len;
}

uint32_t mcu_usart_write(mcu_usart_config_t* config, const uint8_t* data, uint32_t len) {
  (void)config;

  cr_assert_leq(echoed_len + len, sizeof(echoed));
  memcpy(&echoed[echoed_len], data, len);
  echoed_len += len;
  return len;
}

static void reset(void) {
  memset(received, 'A', sizeof(received));
  memset(echoed, 0, sizeof(echoed));
  read_buffer = NULL;
  echoed_len = 0;
}

Test(serial_echo, bounds_maximum_length_input, .init = reset) {
  serial_echo();

  cr_assert_eq(echoed_len, MCU_USART_RX_BUFFER_LEN - 1);
  for (size_t i = 0; i < echoed_len; i++) {
    cr_assert_eq(echoed[i], 'A');
  }

  cr_assert_not_null(read_buffer);
  for (size_t i = 0; i < MCU_USART_RX_BUFFER_LEN; i++) {
    cr_assert_eq(read_buffer[i], 0);
  }
}
