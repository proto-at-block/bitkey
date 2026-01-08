#pragma once

#include "mcu_gpio_impl.h"

#include <stdbool.h>
#include <stdint.h>

typedef enum {
  MCU_GPIO_LOW = 0,
  MCU_GPIO_HIGH,
  MCU_GPIO_HIGH_Z,
  MCU_GPIO_STATE_CNT,
} mcu_gpio_state_e;

void mcu_gpio_init(void);

void mcu_gpio_set(const mcu_gpio_config_t* gpio);
void mcu_gpio_clear(const mcu_gpio_config_t* gpio);
uint32_t mcu_gpio_read(const mcu_gpio_config_t* gpio);
void mcu_gpio_output_set(const mcu_gpio_config_t* gpio, const bool set);
void mcu_gpio_configure(const mcu_gpio_config_t* gpio, const bool output_set);
void mcu_gpio_set_mode(const mcu_gpio_config_t* gpio, mcu_gpio_mode_e mode, const bool output_set);

bool mcu_gpio_int_enable(const mcu_gpio_config_t* gpio, const bool rising, const bool falling,
                         uint32_t* int_num);
void mcu_gpio_int_clear(const uint32_t flags);
uint32_t mcu_gpio_int_get_enabled_odd(void);
uint32_t mcu_gpio_int_get_enabled_even(void);
