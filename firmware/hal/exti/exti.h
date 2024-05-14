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

// Signal the EXTI event from firmware instead of a GPIO.
// This is sort of hacky and breaks the abstraction of this library.
// We use it ONLY to cancel wait-for-finger-down in lib/bio.
//
// This could be made better: add a cancel bit to the event group; one per GPIO.
// Then, update `exti_wait` to take a `bool cancellable` which modifies if we
// `wait_for_all`. But we don't really need to solve the problem generically right now.
void exti_signal(const exti_config_t* config);
