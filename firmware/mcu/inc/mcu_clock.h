#pragma once

#include "mcu_clock_impl.h"

#include <stdint.h>

/**
 * @brief Returns the system clock frequency.
 *
 * @return System clock frequency in hertz.
 */
uint32_t mcu_clock_get_freq(void);
