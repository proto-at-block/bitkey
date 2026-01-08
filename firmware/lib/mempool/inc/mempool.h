#pragma once

#include "arithmetic.h"
#include "rtos.h"

#include <stddef.h>
#include <stdint.h>

typedef struct {
  uint32_t entry_size;
  uint32_t count;
  uint32_t total_size;
  uint8_t* buffer;
  bool* freelist;
} mempool_region_t;

typedef struct {
  uint32_t num_regions;
  mempool_region_t* regions;
  rtos_mutex_t mutex;
} mempool_t;

// Don't call these macros directly. See public API below.

#define _MEMPOOL_DECLARE_REGION(_pool_name, _region_name, _size, _count) \
  {.entry_size = _size, .count = _count, .total_size = _size * _count},

// TODO(W-4581)
#define _MEMPOOL_DECLARE_REGION_BUFFER(_pool_name, _region_name, _size, _count)                  \
  static uint8_t SHARED_TASK_BSS _##_pool_name##_##_region_name##_buffer[_size * _count] = {0};  \
  static bool SHARED_TASK_BSS _##_pool_name##_##_region_name##_freelist[_count] = {false};       \
  for (unsigned int i = 0; i < _count; i++) _##_pool_name##_##_region_name##_freelist[i] = true; \
  _##_pool_name##_mempool.regions[_region_idx].buffer =                                          \
    &_##_pool_name##_##_region_name##_buffer[0];                                                 \
  _##_pool_name##_mempool.regions[_region_idx].freelist =                                        \
    &_##_pool_name##_##_region_name##_freelist[0];                                               \
  _region_idx++;

/**
 * mempool_create() - Create a mempool with the regions specified by REGIONS.
 * @name: Name to use for statically allocated buffers associated with this pool.
 *
 * Usage of this macro requires definition of a list of mempool regions as follows:
 *  #define REGIONS(X)       \
 *    X(my_pool, r0, 4, 1)   \
 *    X(my_pool, r1, 8, 2)   \
 *    X(my_pool, r2, 12, 3)
 *  mempool_t* pool = mempool_create(my_pool);
 *  #undef REGIONS
 *
 * The regions MUST BE DECLARED IN ASCENDING ORDER of entry size.
 *
 * Each region is a 4-tuple of (pool_name, region_name, entry_size, count).
 * `entry_size` is the size of buffers in that region, and `count` is the number of
 * those buffers.
 *
 * The above example will create a mempool with three regions which can be thought of as:
 * my_pool:
 *  r0: [[0,0,0,0]] -- one 4-byte buffer.
 *  r1: [[0,0,0,0,0,0,0,0], [0,0,0,0,0,0,0,0]] -- two 8-byte buffers.
 *  r2: etc.
 */
#define mempool_create(name)                                                       \
  ({                                                                               \
    uint8_t _region_idx = 0;                                                       \
    static mempool_region_t SHARED_TASK_DATA _##name##_regions[] = {               \
      REGIONS(_MEMPOOL_DECLARE_REGION)};                                           \
    static mempool_t SHARED_TASK_DATA _##name##_mempool = {                        \
      .regions = _##name##_regions, .num_regions = ARRAY_SIZE(_##name##_regions)}; \
    REGIONS(_MEMPOOL_DECLARE_REGION_BUFFER)                                        \
    rtos_mutex_create(&_##name##_mempool.mutex);                                   \
    &_##name##_mempool;                                                            \
  })

/**
 * mempool_alloc() - Get a buffer from a mempool.
 * @pool: The mempool_t to get a buffer from.
 * @size: Size of the desired buffer.
 *
 * This function will return NULL if the pool is out of free memory, or if the
 * size doesn't fit into any of the pool's regions.
 *
 * You must call mempool_free() to ensure no leaks in the pool.
 *
 * Example:
 *  uint8_t* buf = mempool_alloc(pool, 4);
 *  ASSERT(buf != NULL);
 *  // use buf...
 *  mempool_free(pool, buf);
 *
 * Context: This function is thread-safe.
 * Return: The allocated buffer or NULL in case of error.
 */
void* mempool_alloc(mempool_t* pool, uint32_t size);

/**
 * mempool_free() - Put a buffer back into a mempool.
 * @pool: The mempool_t to put the buffer back to.
 * @buffer: The buffer.
 *
 * This function will ASSERT on various conditions, such as the buffer
 * not actually belonging to the pool.
 *
 * Example:
 *  uint8_t* buf = mempool_alloc(pool, 4);
 *  ASSERT(buf != NULL);
 *  // use buf...
 *  mempool_free(pool, buf);
 *
 * Context: This function is thread-safe.
 */
void mempool_free(mempool_t* pool, void* buffer);
