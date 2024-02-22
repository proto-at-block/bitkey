#pragma once

#include "indexfs.h"

#include <stdbool.h>
#include <stdint.h>

bool indexfs_monotonic_init(indexfs_t* fs);
bool indexfs_monotonic_valid(indexfs_t* fs);
uint16_t indexfs_monotonic_count(indexfs_t* fs);
bool indexfs_monotonic_increment(indexfs_t* fs);
bool indexfs_monotonic_clear(indexfs_t* fs);
uint8_t indexfs_monotonic_get_flag(indexfs_t* fs);
bool indexfs_monotonic_set_flag(indexfs_t* fs, const uint8_t flag);
