#pragma once

#include "exti.h"
#include "mcu_spi.h"

typedef struct {
  mcu_spi_config_t spi_config;
  mcu_gpio_config_t rst;
  exti_config_t exti;
} bio_config_t;
