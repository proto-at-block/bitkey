#include "mcu_nvic.h"

#include "stm32u5xx.h"

#include <stdint.h>

void mcu_nvic_enable_irq(mcu_irqn_t irq) {
  NVIC_EnableIRQ(irq);
}

void mcu_nvic_set_priority(mcu_irqn_t irq, uint32_t priority) {
  NVIC_SetPriority(irq, priority);
}
