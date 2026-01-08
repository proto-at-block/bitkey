#include "canary.h"

#include "fatal.h"

#include <stdbool.h>
#include <stddef.h>

// Stack canary guard variable used by GCC's -fstack-protector
// This must be defined for stack protection to work.
uintptr_t __attribute__((weak)) __stack_chk_guard = 0xDEADBEEFu;

void __wrap___stack_chk_fail(void) {
  mcu_reset_with_reason(MCU_RESET_STACK_SMASHING_DETECTED);

  FATAL()

  while (1) {
  }
}
