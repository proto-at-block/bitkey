#include "FuzzedDataProvider.h"

extern "C" {
#include "mempool.h"
#include "mempool_impl.h"
}

#include "fff.h"

DEFINE_FFF_GLOBALS;
FAKE_VOID_FUNC(rtos_mutex_create, rtos_mutex_t*);
FAKE_VOID_FUNC(refresh_auth);
FAKE_VALUE_FUNC(bool, rtos_mutex_lock, rtos_mutex_t*);
FAKE_VALUE_FUNC(bool, rtos_mutex_unlock, rtos_mutex_t*);

#define R0_MAX_BUFS 5
#define R0_MAX_SIZE 16
#define R1_MAX_BUFS 5
#define R1_MAX_SIZE 32

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  FuzzedDataProvider fuzzed_data(data, size);

#define REGIONS(X)                         \
  X(my_pool, r0, R0_MAX_SIZE, R0_MAX_BUFS) \
  X(my_pool, r1, R1_MAX_SIZE, R1_MAX_BUFS)
  mempool_t* pool = mempool_create(my_pool);
#undef REGIONS

  /* abstract regions so that we can fuzz multiple (and easily add more) */
  std::vector<uint8_t*> r0_alloc_list;
  std::vector<uint8_t*> r1_alloc_list;
  std::vector<std::vector<uint8_t*>> alloc_region_list = {r0_alloc_list, r1_alloc_list};

  std::vector<uint32_t> max_sizes = {R0_MAX_SIZE, R1_MAX_SIZE};
  std::vector<uint32_t> max_bufs = {R0_MAX_BUFS, R1_MAX_BUFS};

  uint8_t* buf;
  std::vector<uint8_t> buf_data;
  mempool_region_t* region;

  enum FUZZ_CASES { ALLOC, MEMCPY, FREE, kMaxValue = FREE };

  while (fuzzed_data.remaining_bytes() > 0) {
    FUZZ_CASES switch_case = fuzzed_data.ConsumeEnum<FUZZ_CASES>();
    uint32_t region_num = fuzzed_data.ConsumeIntegralInRange(0, 1);  // update if more regions added

    switch (switch_case) {
      case ALLOC:  // alloc a buffer of max region size
        if (alloc_region_list[region_num].size() < max_bufs[region_num]) {
          uint32_t buf_size;
          buf_size = max_sizes[region_num];
          buf = (uint8_t*)mempool_alloc(pool, buf_size);

          buf_data = fuzzed_data.ConsumeBytes<uint8_t>(buf_size);
          memcpy(buf, buf_data.data(), buf_data.size());
          alloc_region_list[region_num].push_back(buf);

          region = mempool_region_for_idx(pool, region_num);
          assert(region == mempool_region_for_buf(pool, buf));
        }
        break;
      case MEMCPY:  // memcpy over an existing buffer
        if (!alloc_region_list[region_num].empty()) {
          uint32_t buf_size;
          uint32_t buf_idx;  // let fuzzer choose which buffer to memcpy
          buf_idx = fuzzed_data.ConsumeIntegralInRange<uint32_t>(
            0, alloc_region_list[region_num].size() - 1);
          buf = alloc_region_list[region_num][buf_idx];

          buf_size = fuzzed_data.ConsumeIntegralInRange<uint32_t>(0, max_sizes[region_num]);
          buf_data = fuzzed_data.ConsumeBytes<uint8_t>(buf_size);
          memcpy(buf, buf_data.data(), buf_data.size());
        }
        break;
      case FREE:  // free an existing buffer and remove from list
        if (!alloc_region_list[region_num].empty()) {
          uint32_t buf_idx;  // let fuzzer choose which buffer to memcpy
          buf_idx = fuzzed_data.ConsumeIntegralInRange<uint32_t>(
            0, alloc_region_list[region_num].size() - 1);
          buf = alloc_region_list[region_num][buf_idx];
          alloc_region_list[region_num].erase(alloc_region_list[region_num].begin() + buf_idx);
          mempool_free(pool, buf);
        }
        break;
      default:
        break;
    }
  }

  // free any remaining
  for (auto& list : alloc_region_list) {
    for (auto& element : list) {
      mempool_free(pool, element);
      element = NULL;
    }
  }

  return 0;
}
