#pragma once

#include "power.h"

#include <stdbool.h>

void max77734_init(void);
bool max77734_validate(void);
void max77734_irq_enable(exti_config_t* irq);
bool max77734_irq_wait(exti_config_t* irq, uint32_t timeout_ms);
void max77734_irq_clear(void);
void max77734_charge_enable(const bool enabled);
void max77734_set_max_charge_cv(const bool max);
void max77734_fast_charge(void);
void max77734_charging_status(bool* charging, bool* chgin_valid);
void max77734_print_registers(void);
void max77734_print_status(void);
void max77734_print_mode(void);
