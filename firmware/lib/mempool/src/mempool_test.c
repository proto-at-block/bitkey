#include "fff.h"
#include "mempool.h"
#include "mempool_impl.h"

#include <criterion/criterion.h>

DEFINE_FFF_GLOBALS;
FAKE_VOID_FUNC(rtos_mutex_create, rtos_mutex_t*);
FAKE_VALUE_FUNC(bool, rtos_mutex_lock, rtos_mutex_t*);
FAKE_VALUE_FUNC(bool, rtos_mutex_unlock, rtos_mutex_t*);

Test(mempool, create) {
#define REGIONS(X)     \
  X(my_pool, r0, 1, 2) \
  X(my_pool, r1, 2, 2) \
  X(my_pool, r2, 3, 2) \
  X(my_pool, r3, 4, 2)
  mempool_t* pool = mempool_create(my_pool);
#undef REGIONS

  cr_assert(pool);
  cr_assert(pool->num_regions == 4);

  cr_assert(pool->regions[0].entry_size == 1);
  cr_assert(pool->regions[1].entry_size == 2);
  cr_assert(pool->regions[2].entry_size == 3);
  cr_assert(pool->regions[3].entry_size == 4);

  cr_assert(pool->regions[0].count == 2);
  cr_assert(pool->regions[1].count == 2);
  cr_assert(pool->regions[2].count == 2);
  cr_assert(pool->regions[3].count == 2);

  cr_assert(pool->regions[0].total_size == 2);
  cr_assert(pool->regions[1].total_size == 4);
  cr_assert(pool->regions[2].total_size == 6);
  cr_assert(pool->regions[3].total_size == 8);
}

Test(mempool, alloc_gets_right_sized_region) {
#define REGIONS(X)       \
  X(my_pool, r0, 4, 2)   \
  X(my_pool, r1, 32, 4)  \
  X(my_pool, r2, 128, 2) \
  X(my_pool, r3, 1024, 2)
  mempool_t* pool = mempool_create(my_pool);
#undef REGIONS

  uint32_t size = 1;
  uint8_t* buf = mempool_alloc(pool, size);
  cr_assert(buf);
  mempool_region_t* region = mempool_region_for_size(pool, size);
  cr_assert(region);
  // Buffer should have come from region 0.
  cr_assert(buf == mempool_region_start_addr(region));
  cr_assert(buf == pool->regions[0].buffer);

  // Repeat this check for each size.
  size = 32;
  buf = mempool_alloc(pool, size);
  cr_assert(buf);
  region = mempool_region_for_size(pool, size);
  cr_assert(region);
  cr_assert(buf == mempool_region_start_addr(region));
  cr_assert(buf == pool->regions[1].buffer);

  size = 127;
  buf = mempool_alloc(pool, size);
  cr_assert(buf);
  region = mempool_region_for_size(pool, size);
  cr_assert(region);
  cr_assert(buf == mempool_region_start_addr(region));
  cr_assert(buf == pool->regions[2].buffer);

  size = 999;
  buf = mempool_alloc(pool, size);
  cr_assert(buf);
  region = mempool_region_for_size(pool, size);
  cr_assert(region);
  cr_assert(buf == mempool_region_start_addr(region));
  cr_assert(buf == pool->regions[3].buffer);

  // Check invalid size.
  size = 1025;
  buf = mempool_alloc(pool, size);
  cr_assert(buf == NULL);
  region = mempool_region_for_size(pool, size);
  cr_assert(region == NULL);
}

Test(mempool, free_buffer) {
#define REGIONS(X)       \
  X(my_pool, r0, 4, 2)   \
  X(my_pool, r1, 32, 4)  \
  X(my_pool, r2, 128, 2) \
  X(my_pool, r3, 1024, 2)
  mempool_t* pool = mempool_create(my_pool);
#undef REGIONS

  // Allocate two buffers and then free them

  cr_assert(&pool->regions[1].freelist[0]);
  uint8_t* buf1 = mempool_alloc(pool, 32);
  mempool_region_t* region = mempool_region_for_buf(pool, buf1);
  cr_assert(region == &pool->regions[1]);
  cr_assert(region->freelist[0] == false);  // Should be marked as used
  cr_assert(region->freelist[1]);
  cr_assert(region->freelist[2]);
  cr_assert(region->freelist[3]);

  uint8_t* buf2 = mempool_alloc(pool, 32);
  mempool_region_t* region2 = mempool_region_for_buf(pool, buf2);
  cr_assert(region2 == &pool->regions[1]);
  cr_assert(region->freelist[0] == false);
  cr_assert(region->freelist[1] == false);
  cr_assert(region->freelist[2]);
  cr_assert(region->freelist[3]);

  mempool_free(pool, buf2);
  cr_assert(region->freelist[0] == false);
  cr_assert(region->freelist[1]);
  cr_assert(region->freelist[2]);
  cr_assert(region->freelist[3]);

  mempool_free(pool, buf1);
  cr_assert(region->freelist[0]);
  cr_assert(region->freelist[1]);
  cr_assert(region->freelist[2]);
  cr_assert(region->freelist[3]);
}

Test(mempool, free_invalid_buffer, .exit_code = 1) {
#define REGIONS(X) X(my_pool, r0, 4, 2)
  mempool_t* pool = mempool_create(my_pool);
#undef REGIONS

  uint8_t* invalid_buf = (uint8_t*)pool->regions - 4;
  mempool_free(pool, invalid_buf);
}

Test(mempool, free_invalid_buffer2, .exit_code = 1) {
#define REGIONS(X) X(my_pool, r0, 4, 2)
  mempool_t* pool = mempool_create(my_pool);
#undef REGIONS

  mempool_region_t* last_region = &pool->regions[pool->num_regions - 1];
  uint8_t* invalid_buf = &last_region->buffer[last_region->total_size + 1];
  mempool_free(pool, invalid_buf);
}

Test(mempool, double_free, .exit_code = 2) {
#define REGIONS(X) X(my_pool, r0, 4, 2)
  mempool_t* pool = mempool_create(my_pool);
#undef REGIONS

  uint8_t* buf = mempool_alloc(pool, 4);
  mempool_free(pool, buf);
  mempool_free(pool, buf);
}

Test(mempool, fill_a_region) {
#define REGIONS(X) X(my_pool, r0, 4, 2)
  mempool_t* pool = mempool_create(my_pool);
#undef REGIONS

  uint8_t* buf1 = mempool_alloc(pool, 2);
  uint8_t* buf2 = mempool_alloc(pool, 2);
  cr_assert(buf1);
  cr_assert(buf2);
  cr_assert(buf1 < buf2);

  mempool_region_t* region = mempool_region_for_size(pool, 2);
  cr_assert(buf1 == mempool_region_start_addr(region));
  cr_assert(buf2 == mempool_region_end_addr(region) - 4);
  cr_assert(buf2 == mempool_region_start_addr(region) + 4);

  // Another allocation would fill the region. This is tested in the `oom` test.

  // Free and check.
  mempool_free(pool, buf1);
  uint8_t* buf3 = mempool_alloc(pool, 2);
  cr_assert(buf3);
  cr_assert(buf1 == buf3);
}

Test(mempool, fill_pattern) {
#define REGIONS(X)     \
  X(my_pool, r0, 4, 1) \
  X(my_pool, r1, 8, 1) \
  X(my_pool, r2, 12, 1)
  mempool_t* pool = mempool_create(my_pool);
#undef REGIONS

  uint8_t* buf1 = mempool_alloc(pool, 4);
  memset(buf1, 'a', 4);
  uint8_t* buf2 = mempool_alloc(pool, 8);
  memset(buf2, 'b', 8);
  uint8_t* buf3 = mempool_alloc(pool, 12);
  memset(buf3, 'c', 12);

  mempool_region_t* r0 = mempool_region_for_idx(pool, 0);
  mempool_region_t* r1 = mempool_region_for_idx(pool, 1);
  mempool_region_t* r2 = mempool_region_for_idx(pool, 2);

  uint8_t r0_expected[] = {'a', 'a', 'a', 'a'};
  cr_assert(memcmp(r0->buffer, &r0_expected[0], 4) == 0);

  uint8_t r1_expected[] = {'b', 'b', 'b', 'b', 'b', 'b', 'b', 'b'};
  cr_assert(memcmp(r1->buffer, &r1_expected[0], 8) == 0);

  uint8_t r2_expected[] = {'c', 'c', 'c', 'c', 'c', 'c', 'c', 'c', 'c', 'c', 'c', 'c'};
  cr_assert(memcmp(r2->buffer, &r2_expected[0], 12) == 0);
}

Test(mempool, single_entry) {
#define REGIONS(X) X(my_pool, r0, 100 * 100, 1)
  mempool_t* pool = mempool_create(my_pool);
#undef REGIONS

  uint8_t* buf = mempool_alloc(pool, 9548);
  mempool_free(pool, buf);
}

Test(mempool, fpc_usage_pattern) {
#define REGIONS(X)        \
  X(my_pool, r0, 256, 5)  \
  X(my_pool, r1, 3000, 5) \
  X(my_pool, r2, 100 * 100, 1)
  mempool_t* pool = mempool_create(my_pool);
#undef REGIONS

  uint8_t* a = mempool_alloc(pool, 147);
  uint8_t* b = mempool_alloc(pool, 1024);
  mempool_free(pool, b);
  uint8_t* c = mempool_alloc(pool, 936);
  uint8_t* d = mempool_alloc(pool, 68);
  uint8_t* e = mempool_alloc(pool, 2306);
  mempool_free(pool, e);
  mempool_free(pool, d);
  uint8_t* f = mempool_alloc(pool, 48);
  uint8_t* g = mempool_alloc(pool, 9548);
  uint8_t* h = mempool_alloc(pool, 936);
  mempool_free(pool, h);
  mempool_free(pool, g);
  mempool_free(pool, a);
  mempool_free(pool, f);
  mempool_free(pool, c);
}

Test(mempool, oom, .exit_code = 1) {
#define REGIONS(X) X(my_pool, r0, 1, 1)
  mempool_t* pool = mempool_create(my_pool);
#undef REGIONS

  uint8_t* buf = mempool_alloc(pool, 1);
  cr_assert(buf);
  uint8_t* buf2 = mempool_alloc(pool, 1);
  cr_assert(buf2 == NULL);
  mempool_free(pool, buf2);
}
