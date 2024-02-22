#include "mcu_clock.h"

#include "em_cmu.h"

uint32_t mcu_clock_get_freq(void) {
  return CMU_ClockFreqGet(cmuClock_CORE);
}
