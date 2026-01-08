#include "mcu_debug.h"

#include "stm32u5xx.h"

#include <stdbool.h>
#include <stdint.h>

void mcu_debug_dwt_enable(void) {
  CoreDebug->DEMCR |= CoreDebug_DEMCR_TRCENA_Msk;
  DWT->CYCCNT = 0;
  DWT->CTRL |= DWT_CTRL_CYCCNTENA_Msk;
}

uint32_t mcu_debug_dwt_cycle_counter(void) {
  return DWT->CYCCNT;
}

bool mcu_debug_debugger_attached(void) {
  return ((CoreDebug->DHCSR & CoreDebug_DHCSR_C_DEBUGEN_Msk) != 0u);
}

void mcu_debug_break(void) {
  __BKPT(0);
}
