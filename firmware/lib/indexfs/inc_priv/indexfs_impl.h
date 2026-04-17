#pragma once

#include "attributes.h"

#include <stdbool.h>
#include <stdint.h>

EXTERN_VISIBLE_FOR_TESTING(bool erase_flash(const uint32_t addr, const uint32_t size));
EXTERN_VISIBLE_FOR_TESTING(bool addr_in_range(const uint32_t addr, const uint32_t range_start,
                                              const uint32_t range_size));
