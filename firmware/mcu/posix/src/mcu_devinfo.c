#include "mcu_devinfo.h"

#include <string.h>

void mcu_devinfo_chipid(uint8_t chipid[CHIPID_LENGTH]) {
  if (chipid == NULL) {
    return;
  }

  // POSIX-ID
  static const uint8_t posix_chipid[CHIPID_LENGTH] = {
    0x50, 0x4f, 0x53, 0x49, 0x58, 0x2d, 0x49, 0x44,
  };

  memcpy(chipid, posix_chipid, CHIPID_LENGTH);
}
