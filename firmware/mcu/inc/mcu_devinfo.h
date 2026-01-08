#pragma once

#include "mcu_devinfo_impl.h"

#include <stdint.h>

/**
 * @brief Reads the device's chip information.
 *
 * @param chipid  Pointer to the buffer to populate with the chip ID.
 */
void mcu_devinfo_chipid(uint8_t chipid[CHIPID_LENGTH]);
