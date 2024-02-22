#include "mcu_wdog.h"

#include "assert.h"
#include "mcu_reset.h"

#include "em_cmu.h"
#include "em_wdog.h"

#include <stdint.h>

void mcu_wdog_init(void) {
  CMU_ClockEnable(cmuClock_WDOG0, true);

  WDOG_Init_TypeDef settings = WDOG_INIT_DEFAULT;
  CMU_ClockSelectSet(cmuClock_WDOG0, cmuSelect_ULFRCO);
  settings.perSel = wdogPeriod_8k;  // 8193 clock cycles of a 1kHz clock = ~8 seconds period

  WDOGn_Init(DEFAULT_WDOG, &settings);
}

void mcu_wdog_feed(void) {
  WDOGn_Feed(DEFAULT_WDOG);
}

void WDOG0_IRQHandler(void) {
  mcu_reset_with_reason(MCU_RESET_WATCHDOG_TIMEOUT);
}

void WDOG1_IRQHandler(void) {
  mcu_reset_with_reason(MCU_RESET_WATCHDOG_TIMEOUT);
}
