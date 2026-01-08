#include "mcu_reset.h"

#include "attributes.h"
#include "fatal.h"
#include "mcu_debug.h"
#include "stm32u5xx.h"

// Reset reason stored in no-init RAM (preserved across resets)
SECTION(".noinit.reset_reason") USED static volatile mcu_reset_info_t info;

NO_RETURN void mcu_reset_with_reason(const mcu_reset_reason_t reason) {
  mcu_reset_set_reason(reason);

  if (mcu_debug_debugger_attached()) {
    // If debugger is attached, then break here for the developer.
    mcu_debug_break();
  }

  NVIC_SystemReset();

  // Should not be reached. If it somehow is, crash so that execution may not resume.
  FATAL();
  __builtin_unreachable();
}

void mcu_reset_set_reason(const mcu_reset_reason_t reason) {
  info.reason = reason;
}

mcu_reset_reason_t mcu_reset_get_reason(void) {
  return info.reason;
}

// Get reset cause from RCC control/status register
uint32_t mcu_reset_rmu_cause_get(void) {
  return RCC->CSR;
}

// Clear reset flags
void mcu_reset_rmu_clear(void) {
  RCC->CSR |= RCC_CSR_RMVF;
}
