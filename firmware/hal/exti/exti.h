#pragma once

#include "mcu_gpio.h"

typedef enum {
  EXTI_TRIGGER_RISING,
  EXTI_TRIGGER_FALLING,
  EXTI_TRIGGER_BOTH,
} exti_trigger_t;

typedef struct {
  mcu_gpio_config_t gpio;
  exti_trigger_t trigger;
} exti_config_t;

void exti_enable(const exti_config_t* config);
bool exti_pending(const exti_config_t* config);
void exti_clear(const exti_config_t* config);
bool exti_wait(const exti_config_t* config, const uint32_t timeout_ms, bool clear);
