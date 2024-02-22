#pragma once

#include <stdbool.h>
#include <stdint.h>

bool battery_set_variant(const uint32_t variant);
bool battery_get_variant(uint32_t* variant);
void battery_print_variant(void);
