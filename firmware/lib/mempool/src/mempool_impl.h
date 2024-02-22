#pragma once

#include "mempool.h"

mempool_region_t* mempool_region_for_size(mempool_t* pool, uint32_t size);
mempool_region_t* mempool_region_for_buf(mempool_t* pool, uint8_t* buffer);
mempool_region_t* mempool_region_for_idx(mempool_t* pool, uint32_t idx);

uint8_t* mempool_region_start_addr(mempool_region_t* region);
uint8_t* mempool_region_end_addr(mempool_region_t* region);

bool mempool_region_contains_buf(mempool_region_t* region, uint8_t* buffer);
