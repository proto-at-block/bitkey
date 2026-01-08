#include "mcu.h"
#include "mcu_reset.h"
#include "mpu_regions.h"

#include <assert.h>

void MemManage_Handler(void) {
  assert(0);
  mcu_reset_with_reason(MCU_RESET_FAULT);
}

__attribute__((weak)) void mpu_regions_init(void) {}
