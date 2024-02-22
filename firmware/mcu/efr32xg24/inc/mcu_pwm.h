#pragma once

#include "mcu_gpio.h"
#include "mcu_timer.h"

#include <stdbool.h>
#include <stdint.h>

typedef enum {
  PWM_ACTIVE_HIGH = 0,
  PWM_ACTIVE_LOW = 1,
} mcu_pwm_polarity_t;

typedef struct {
  mcu_timer_t* timer;
  uint8_t timer_channel;
  mcu_gpio_config_t gpio;
  uint8_t gpio_location;
  int frequency;
  int default_duty_cycle;
  mcu_pwm_polarity_t polarity;
} mcu_pwm_t;

void mcu_pwm_init(mcu_pwm_t* pwm);
void mcu_pwm_deinit(mcu_pwm_t* pwm);

void mcu_pwm_start(mcu_pwm_t* pwm);
void mcu_pwm_stop(mcu_pwm_t* pwm);

void mcu_pwm_set_duty_cycle(mcu_pwm_t* pwm, uint16_t duty);
uint16_t mcu_pwm_get_duty_cycle(mcu_pwm_t* pwm);
