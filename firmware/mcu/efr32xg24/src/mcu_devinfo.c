
#include "mcu_devinfo.h"

#include "assert.h"

#include <string.h>

void mcu_devinfo_chipid(uint8_t chipid[CHIPID_LENGTH]) {
#if defined(_DEVINFO_EUI64H_MASK)
  uint32_t lo = DEVINFO->EUI64L;
  uint32_t hi = DEVINFO->EUI64H;
  // Matches the output of `commander device info --device efr32mg24`
  chipid[0] = (uint8_t)((hi >> 24) & 0xff);
  chipid[1] = (uint8_t)((hi >> 16) & 0xff);
  chipid[2] = (uint8_t)((hi >> 8) & 0xff);
  chipid[3] = (uint8_t)((hi >> 0) & 0xff);
  chipid[4] = (uint8_t)((lo >> 24) & 0xff);
  chipid[5] = (uint8_t)((lo >> 16) & 0xff);
  chipid[6] = (uint8_t)((lo >> 8) & 0xff);
  chipid[7] = (uint8_t)((lo >> 0) & 0xff);
#else
#error Location of device unique number is not defined.
#endif
}

void mcu_devinfo_read(mcu_devinfo_t* devinfo) {
  ASSERT(devinfo);
  memcpy(devinfo, DEVINFO, sizeof(mcu_devinfo_t));
}
