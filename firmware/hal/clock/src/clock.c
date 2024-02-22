#include "clock.h"

#include "mcu_clock.h"

uint32_t clock_get_freq(void) {
  return mcu_clock_get_freq();
}
