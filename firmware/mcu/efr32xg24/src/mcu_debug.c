#include "mcu_debug.h"

#include "em_core.h"

void mcu_debug_dwt_enable(void) {
  CoreDebug->DEMCR |= CoreDebug_DEMCR_TRCENA_Msk;
  DWT->CYCCNT = 0;
  DWT->CTRL |= DWT_CTRL_CYCCNTENA_Msk;
}

uint32_t mcu_debug_dwt_cycle_counter(void) {
  return DWT->CYCCNT;
}
