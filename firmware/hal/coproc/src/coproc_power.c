#include "coproc_power.h"

#include "coproc.h"
#include "mcu_gpio.h"
#include "rtos.h"

extern coproc_cfg_t _coproc_cfg;

void coproc_power_on(void) {
  if (_coproc_cfg.power.en.gpio != NULL) {
    /* Note: Assumes active-high. */
    mcu_gpio_set(_coproc_cfg.power.en.gpio);
  }

  coproc_power_deassert_reset();
}

void coproc_power_off(void) {
  if (_coproc_cfg.power.en.gpio != NULL) {
    /* Note: Assumes active-high. */
    mcu_gpio_clear(_coproc_cfg.power.en.gpio);
  }

  coproc_power_assert_reset();
  rtos_thread_sleep(_coproc_cfg.power.power_off_duration_ms);
}

void coproc_power_reset(void) {
  coproc_power_assert_reset();
  coproc_power_deassert_reset();
}

void coproc_power_assert_reset(void) {
  if (_coproc_cfg.power.reset.gpio == NULL) {
    return;
  }

  /* Note: Assumes active low. */
  mcu_gpio_clear(_coproc_cfg.power.reset.gpio);
}

void coproc_power_deassert_reset(void) {
  if (_coproc_cfg.power.reset.gpio == NULL) {
    return;
  }

  /* Note: Assumes active low. */
  mcu_gpio_set(_coproc_cfg.power.reset.gpio);
}
