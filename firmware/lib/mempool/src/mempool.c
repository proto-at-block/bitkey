#include "mempool.h"

#include "assert.h"
#include "mempool_impl.h"
#include "wstring.h"

static uint8_t* get_first_free_buffer(mempool_region_t* region) {
  for (uint32_t entry = 0; entry < region->count; entry++) {
    if (region->freelist[entry]) {
      region->freelist[entry] = false;  // Mark as allocated.
      return &region->buffer[entry * region->entry_size];
    }
  }
  return NULL;
}

void* mempool_alloc(mempool_t* pool, uint32_t size) {
  ASSERT(pool);

  rtos_mutex_lock(&pool->mutex);

  uint8_t* buf = NULL;
  mempool_region_t* region = mempool_region_for_size(pool, size);
  ASSERT_E(region, 1);
  buf = get_first_free_buffer(region);

  // We don't handle OOM at all -- it shouldn't happen -- so assert here
  // instead of requiring all callers to do so.
  ASSERT_E(buf, 2);

  rtos_mutex_unlock(&pool->mutex);
  return buf;
}

void mempool_free(mempool_t* pool, void* buffer) {
  ASSERT(pool);

  if (!buffer) {
    return;
  }

  uint8_t* buf = (uint8_t*)buffer;

  rtos_mutex_lock(&pool->mutex);

  mempool_region_t* region = mempool_region_for_buf(pool, buf);
  ASSERT_E(region, 1);

  // Zero out free'd memory in case a caller places security-sensitive data in this
  // mempool, and then reuses it unknowingly.
  memzero(buf, region->entry_size);

  uint32_t entry = (buf - mempool_region_start_addr(region)) / region->entry_size;
  ASSERT(entry < region->count);

  // Check if buffer is actually in use. We don't technically have to assert here,
  // but this catches potential bugs in the caller.
  ASSERT_E(region->freelist[entry] == false, 2);

  region->freelist[entry] = true;

  rtos_mutex_unlock(&pool->mutex);
}

mempool_region_t* mempool_region_for_size(mempool_t* pool, uint32_t size) {
  if (size == 0) {
    return NULL;
  }

  for (uint32_t i = 0; i < pool->num_regions; i++) {
    mempool_region_t* region = &pool->regions[i];
    if (size <= region->entry_size) {
      return region;
    }
  }

  return NULL;
}

mempool_region_t* mempool_region_for_buf(mempool_t* pool, uint8_t* buffer) {
  for (uint32_t i = 0; i < pool->num_regions; i++) {
    mempool_region_t* region = &pool->regions[i];
    if (mempool_region_contains_buf(region, buffer)) {
      return region;
    }
  }
  return NULL;
}

mempool_region_t* mempool_region_for_idx(mempool_t* pool, uint32_t idx) {
  ASSERT(idx < pool->num_regions);
  return &pool->regions[idx];
}

uint8_t* mempool_region_start_addr(mempool_region_t* region) {
  return &region->buffer[0];
}

uint8_t* mempool_region_end_addr(mempool_region_t* region) {
  return &region->buffer[region->total_size];
}

bool mempool_region_contains_buf(mempool_region_t* region, uint8_t* buffer) {
  return (buffer >= mempool_region_start_addr(region)) &&
         (buffer < mempool_region_end_addr(region));
}
