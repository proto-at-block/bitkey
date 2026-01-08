#include "sl_device_init_clocks.h"

#include "em_cmu.h"

#include <stdbool.h>

sl_status_t sl_device_init_clocks(void) {
  CMU_HFRCODPLLBandSet(cmuHFRCODPLLFreq_80M0Hz);
  CMU_ClockSelectSet(cmuClock_SYSCLK,
                     cmuSelect_HFRCODPLL);  // Select reference clock for High Freq. clock

#if defined(_CMU_EM01GRPACLKCTRL_MASK)
  CMU_ClockSelectSet(cmuClock_EM01GRPACLK, cmuSelect_HFRCODPLL);
#endif
#if defined(_CMU_EM01GRPBCLKCTRL_MASK)
  CMU_ClockSelectSet(cmuClock_EM01GRPBCLK, cmuSelect_HFRCODPLL);
#endif
#if defined(_CMU_EM01GRPCCLKCTRL_MASK)
  CMU_ClockSelectSet(cmuClock_EM01GRPCCLK, cmuSelect_HFRCODPLL);
#endif
  CMU_ClockSelectSet(cmuClock_EM23GRPACLK, cmuSelect_LFRCO);
  CMU_ClockSelectSet(cmuClock_EM4GRPACLK, cmuSelect_LFRCO);
#if defined(RTCC_PRESENT)
  CMU_ClockSelectSet(cmuClock_RTCC, cmuSelect_LFRCO);
#endif
#if defined(SYSRTC_PRESENT)
  CMU_ClockSelectSet(cmuClock_SYSRTC, cmuSelect_LFRCO);
#endif
  CMU_ClockSelectSet(cmuClock_WDOG0, cmuSelect_LFRCO);
#if WDOG_COUNT > 1
  CMU_ClockSelectSet(cmuClock_WDOG1, cmuSelect_LFRCO);
#endif

  return SL_STATUS_OK;
}
