#pragma once

#include "stm32u5xx_ll_gpio.h"

#include <stdbool.h>

typedef enum {
  MCU_GPIO_MODE_INPUT = 0b00,
  MCU_GPIO_MODE_OUTPUT = 0b01,
  MCU_GPIO_MODE_ALTERNATE = 0b10,
  MCU_GPIO_MODE_ANALOG = 0b11,
} mcu_gpio_mode_e;

typedef enum {
  MCU_GPIO_AF_MODE_0 = 0b0000,
  MCU_GPIO_AF_MODE_1 = 0b0001,
  MCU_GPIO_AF_MODE_2 = 0b0010,
  MCU_GPIO_AF_MODE_3 = 0b0011,
  MCU_GPIO_AF_MODE_4 = 0b0100,
  MCU_GPIO_AF_MODE_5 = 0b0101,
  MCU_GPIO_AF_MODE_6 = 0b0110,
  MCU_GPIO_AF_MODE_7 = 0b0111,
  MCU_GPIO_AF_MODE_8 = 0b1000,
  MCU_GPIO_AF_MODE_9 = 0b1001,
  MCU_GPIO_AF_MODE_10 = 0b1010,
  MCU_GPIO_AF_MODE_11 = 0b1011,
  MCU_GPIO_AF_MODE_12 = 0b1100,
  MCU_GPIO_AF_MODE_13 = 0b1101,
  MCU_GPIO_AF_MODE_14 = 0b1110,
  MCU_GPIO_AF_MODE_15 = 0b1111
} mcu_gpio_af_mode_e;

typedef enum {
  MCU_GPIO_SPEED_LOW = 0b00,
  MCU_GPIO_SPEED_MEDIUM = 0b01,
  MCU_GPIO_SPEED_HIGH = 0b10,
  MCU_GPIO_SPEED_VERY_HIGH = 0b11
} mcu_gpio_speed_e;

typedef enum {
  MCU_GPIO_PUPD_NONE = 0b00,
  MCU_GPIO_PULL_UP = 0b01,
  MCU_GPIO_PULL_DOWN = 0b10,
} mcu_gpio_pull_pupd_e;

typedef GPIO_TypeDef* mcu_gpio_port_t;
typedef uint32_t mcu_gpio_pin_t;

#define MCU_GPIO_EXTI_MAX 16

typedef struct {
  mcu_gpio_port_t port;
  mcu_gpio_pin_t pin;
  mcu_gpio_mode_e mode;
  mcu_gpio_af_mode_e af;
  mcu_gpio_speed_e speed;
  mcu_gpio_pull_pupd_e pupd;
  bool low_voltage;
} mcu_gpio_config_t;
