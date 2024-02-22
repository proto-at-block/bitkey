#pragma once

#include "mcu_usart.h"

#include <stdbool.h>

typedef struct {
  mcu_usart_config_t usart;
  struct {
    bool enable;
    void* target;
  } retarget_printf;
} serial_config_t;

void serial_init(void);
void serial_echo(void);
