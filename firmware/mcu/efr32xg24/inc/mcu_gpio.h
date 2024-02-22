#pragma once

#include "em_gpio.h"

#include <stdbool.h>
#include <stdint.h>

typedef enum {
  MCU_GPIO_MODE_DISABLED = 0,
  MCU_GPIO_MODE_INPUT,
  MCU_GPIO_MODE_INPUT_PULL,
  MCU_GPIO_MODE_INPUT_PULL_FILTER,
  MCU_GPIO_MODE_PUSH_PULL,
  MCU_GPIO_MODE_PUSH_PULL_DRIVE,
  MCU_GPIO_MODE_WIRED_OR,
  MCU_GPIO_MODE_WIRED_OR_PULLDOWN,
  MCU_GPIO_MODE_OPEN_DRAIN,  // Gecko SDK uses the term 'wired and'.
  MCU_GPIO_MODE_OPEN_DRAIN_FILTER,
  MCU_GPIO_MODE_OPEN_DRAIN_PULLUP,
  MCU_GPIO_MODE_OPEN_DRAIN_PULLUP_FILTER,
  MCU_GPIO_MODE_OPEN_DRAIN_DRIVE,
  MCU_GPIO_MODE_OPEN_DRAIN_DRIVE_FILTER,
  MCU_GPIO_MODE_OPEN_DRAIN_DRIVE_PULLUP,
  MCU_GPIO_MODE_OPEN_DRAIN_DRIVE_PULLUP_FILTER,
} mcu_gpio_mode_e;

typedef struct {
  uint32_t port;
  uint32_t pin;
  mcu_gpio_mode_e mode;
} mcu_gpio_config_t;

#define MCU_GPIO_EXTI_GROUPS (4u)
#define MCU_GPIO_EXTI_MAX    (GPIO_EXTINTNO_MAX)

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
