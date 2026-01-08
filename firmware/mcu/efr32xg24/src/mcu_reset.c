#include "mcu_reset.h"

#include "attributes.h"
#include "fatal.h"
#include "mcu_reset_impl.h"

#include "em_device.h"

SECTION(".noinit.reset_reason") USED static volatile mcu_reset_info_t info;

NO_RETURN void __mcu_reset_with_reason(mcu_reset_reason_t reason) {
  mcu_reset_set_reason(reason);
  NVIC_SystemReset();
  FATAL();  // Should not be reached. If it somehow is, crash so that execution may not resume.
  __builtin_unreachable();
}

void mcu_reset_set_reason(mcu_reset_reason_t reason) {
  info.reason = reason;
}

mcu_reset_reason_t mcu_reset_get_reason(void) {
  return info.reason;
}

void mcu_reset_rmu_clear(void) {
  EMU->CMD_SET = EMU_CMD_RSTCAUSECLR;
}

uint32_t mcu_reset_rmu_cause_get(void) {
  return EMU->RSTCAUSE;
}
