#pragma once

#include "em_device.h"

typedef IRQn_Type mcu_irqn_t;

void mcu_nvic_enable_irq(mcu_irqn_t irq);
void mcu_nvic_set_priority(mcu_irqn_t irq, uint32_t priority);
