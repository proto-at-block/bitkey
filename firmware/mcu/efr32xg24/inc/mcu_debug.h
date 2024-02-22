#pragma once

#include "stdint.h"

void mcu_debug_dwt_enable(void);
uint32_t mcu_debug_dwt_cycle_counter(void);
