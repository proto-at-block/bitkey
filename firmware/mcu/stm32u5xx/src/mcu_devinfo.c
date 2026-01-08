#include "mcu_devinfo.h"

#include "assert.h"
#include "stm32u5xx.h"

#include <stdint.h>
#include <string.h>

void mcu_devinfo_chipid(uint8_t chipid[CHIPID_LENGTH]) {
  ASSERT(chipid != NULL);
  memcpy(chipid, (void*)UID_BASE, CHIPID_LENGTH);
}
