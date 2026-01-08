#pragma once

#include "exti.h"
#include "power.h"

#include <stdbool.h>
#include <stdint.h>

void max77734_init(const power_ldo_config_t* ldo_config);
bool max77734_validate(void);
void max77734_set_ldo_low_power_mode(void);
void max77734_irq_enable(exti_config_t* irq);
bool max77734_irq_wait(exti_config_t* irq, uint32_t timeout_ms);
void max77734_irq_clear(void);
void max77734_charge_enable(const bool enabled);
void max77734_usb_suspend(const bool enabled);
void max77734_set_max_charge_cv(const bool max);
void max77734_fast_charge(void);
void max77734_charging_status(bool* charging, bool* chgin_valid);
void max77734_print_registers(void);
void max77734_print_status(void);
void max77734_print_mode(void);
power_charger_mode_t max77734_get_mode(void);
void max77734_enable_thermal_interrupts(void);
bool max77734_check_thermal_status(bool* tjal1, bool* tjal2, bool* tj_reg);
uint8_t max77734_get_register_count(void);
void max77734_read_register(uint8_t index, uint8_t* offset_out, uint8_t* value_out);
