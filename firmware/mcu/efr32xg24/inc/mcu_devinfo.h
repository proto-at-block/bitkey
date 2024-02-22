#pragma once

// clang-format off
#include "em_device.h"
#include "efr32mg24_devinfo.h"
// clang-format on

#include <stdint.h>

#define CHIPID_LENGTH    (8)
#define mcu_devinfo_t    DEVINFO_TypeDef
#define MCU_DEVINFO_SIZE (sizeof(mcu_devinfo_t))

void mcu_devinfo_chipid(uint8_t chipid[CHIPID_LENGTH]);
void mcu_devinfo_read(mcu_devinfo_t* devinfo);
