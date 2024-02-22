#include "led.h"

#include "arithmetic.h"
#include "assert.h"
#include "mcu.h"
#include "mcu_pwm.h"

extern led_config_t led_config[];
extern uint32_t LED_COUNT;

void led_init(void) {
  for (led_t led = 0; led < LED_COUNT; led++) {
    led_config_t* cfg = &led_config[led];
    mcu_gpio_configure(&cfg->pwm.gpio, false);
    mcu_pwm_init(&cfg->pwm);
  }
}

void led_deinit(void) {
  for (led_t led = 0; led < LED_COUNT; led++) {
    led_config_t* cfg = &led_config[led];
    mcu_pwm_deinit(&cfg->pwm);
  }
}

void led_on(led_t led, uint32_t duty_cycle) {
  ASSERT(led < LED_END);
  if (led >= LED_COUNT) {
    return;
  }

  led_config_t* cfg = &led_config[led];
  mcu_pwm_set_duty_cycle(&cfg->pwm, duty_cycle);
  mcu_pwm_start(&cfg->pwm);
}

void led_off(led_t led) {
  ASSERT(led < LED_END);
  if (led >= LED_COUNT) {
    return;
  }

  led_config_t* cfg = &led_config[led];
  mcu_pwm_stop(&cfg->pwm);
}
