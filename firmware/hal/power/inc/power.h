#pragma once

#include "exti.h"
#include "mcu_gpio.h"

typedef struct {
  mcu_gpio_config_t power_retain;
  mcu_gpio_config_t five_volt_boost;
  exti_config_t cap_touch_detect;
  struct {
    mcu_gpio_config_t gpio;
    uint32_t hold_ms;
  } cap_touch_cal;
  exti_config_t charger_irq;
  exti_config_t usb_detect_irq;
} power_config_t;

void power_init(void);
void power_set_retain(const bool enabled);
void power_restart_timer(void);
bool power_validate_fuel_gauge(void);
void power_get_battery(uint32_t* soc_millipercent, uint32_t* vcell_mv, int32_t* avg_current_ma,
                       uint32_t* cycles);
void power_fast_charge(void);
bool power_set_battery_variant(const uint32_t variant);
void power_retain_charged_indicator(void);
