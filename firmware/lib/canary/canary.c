#include "canary.h"

#include "fatal.h"

#include <stdbool.h>

void __wrap___stack_chk_fail(void) {
  mcu_reset_with_reason(MCU_RESET_STACK_SMASHING_DETECTED);

  FATAL()

  while (1) {
  }
}
