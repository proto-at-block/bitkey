#pragma once

#include "mcu_pwm.h"

#include <stdint.h>

typedef struct {
  mcu_pwm_t pwm;
} led_config_t;

typedef enum {
  LED_R = 0,
  LED_G,
  LED_B,
  LED_W,
  LED_END,
} led_t;

#define LED_DUTY_MIN  (0ul)
#define LED_DUTY_HALF (UINT16_MAX / 2)
#define LED_DUTY_MAX  (UINT16_MAX)

void led_init(void);
void led_deinit(void);

void led_on(led_t led, uint32_t duty_cycle);
void led_off(led_t led);
