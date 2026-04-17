#pragma once

#include <stdbool.h>
#include <stdint.h>

uint32_t mcu_systick_get_reload(void);
uint32_t mcu_systick_get_value(void);
bool mcu_systick_is_pending(void);
