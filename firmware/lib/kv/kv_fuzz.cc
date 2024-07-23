#include "FuzzedDataProvider.h"

extern "C" {
#include "bd/lfs_emubd.h"
#include "fff.h"
#include "filesystem.h"
#include "hex.h"
#include "kv.h"
#include "rtos.h"

#include <stdio.h>

DEFINE_FFF_GLOBALS;
FAKE_VALUE_FUNC(int, bd_erase_all);
FAKE_VALUE_FUNC(lfs_t*, bd_mount);

bool rtos_mutex_lock(rtos_mutex_t* UNUSED(t)) {
  return true;
}
bool rtos_mutex_take(rtos_mutex_t* UNUSED(a), uint32_t UNUSED(b)) {
  return true;
}
bool rtos_mutex_unlock(rtos_mutex_t* UNUSED(a)) {
  return true;
}
bool rtos_mutex_lock_from_isr(rtos_mutex_t* UNUSED(t)) {
  return true;
}
bool rtos_mutex_unlock_from_isr(rtos_mutex_t* UNUSED(t)) {
  return true;
}
bool rtos_in_isr(void) {
  return false;
}
bool rtos_semaphore_give_from_isr(rtos_semaphore_t* UNUSED(s)) {
  return true;
}
bool rtos_semaphore_take_from_isr(rtos_semaphore_t* UNUSED(s)) {
  return true;
}
bool rtos_semaphore_give(rtos_semaphore_t* UNUSED(s)) {
  return true;
}
bool rtos_semaphore_take(rtos_semaphore_t* UNUSED(s), uint32_t UNUSED(t)) {
  return true;
}

bool bd_error_str(char* UNUSED(a), const size_t UNUSED(b), const int UNUSED(c)) {
  return false;
}

typedef void (*f_cb)(void*);
FAKE_VOID_FUNC(rtos_mutex_create, rtos_mutex_t*);
FAKE_VOID_FUNC(rtos_semaphore_create, rtos_semaphore_t*);
FAKE_VOID_FUNC(rtos_thread_create_static, rtos_thread_t*, f_cb, const char*, void*,
               rtos_thread_priority_t, uint32_t*, uint32_t, StaticTask_t*, rtos_thread_mpu_t);
FAKE_VOID_FUNC(rtos_thread_delete, rtos_thread_t*);
FAKE_VOID_FUNC(rtos_event_group_create, rtos_event_group_t*);
uint32_t rtos_event_group_set_bits(rtos_event_group_t* UNUSED(a), const uint32_t UNUSED(b)) {
  return 1;
}
uint32_t rtos_event_group_clear_bits(rtos_event_group_t* UNUSED(a), const uint32_t UNUSED(b)) {
  return 1;
}
uint32_t rtos_event_group_wait_bits(rtos_event_group_t* UNUSED(a), const uint32_t UNUSED(b),
                                    const bool UNUSED(c), const bool UNUSED(d),
                                    uint32_t UNUSED(e)) {
  return 1;
}
}

extern void kv_print(void);

static lfs_t lfs;
#define FS_BLOCK_CYCLES   (500)          /* Wear leveling cycles */
#define FS_LOOKAHEAD_SIZE (128)          /* Must be a multiple of 8 */
#define FLASH_PAGE_SIZE   (0x00002000UL) /* Flash Memory page size */
#define FS_BLOCKS         16u            /* Number of flash blocks */

static uint8_t lfs_read_buf[FLASH_PAGE_SIZE];
static uint8_t lfs_prog_buf[FLASH_PAGE_SIZE];
static uint8_t lfs_lookahead_buf[FS_LOOKAHEAD_SIZE];
const struct lfs_emubd_config emubd_cfg = {
  .read_size = FLASH_PAGE_SIZE,
  .prog_size = FLASH_PAGE_SIZE,
  .erase_size = FLASH_PAGE_SIZE,
  .erase_count = FS_BLOCK_COUNT,
  .erase_value = -1,
};

static lfs_emubd_t emubd = {0};
rtos_thread_mpu_t _fs_mount_task_regions;

const struct lfs_config cfg = {
  // block device operations
  .read = lfs_emubd_read,
  .prog = lfs_emubd_prog,
  .erase = lfs_emubd_erase,
  .sync = lfs_emubd_sync,

  // block device configuration
  .read_size = FLASH_PAGE_SIZE,
  .prog_size = FLASH_PAGE_SIZE,
  .block_size = FLASH_PAGE_SIZE,
  .block_count = FS_BLOCK_COUNT,
  .cache_size = FLASH_PAGE_SIZE,
  .lookahead_size = FS_LOOKAHEAD_SIZE,
  .block_cycles = FS_BLOCK_CYCLES,

  .read_buffer = lfs_read_buf,
  .prog_buffer = lfs_prog_buf,
  .lookahead_buffer = lfs_lookahead_buf,

  .context = &emubd,
};

static void init_lfs() {
  lfs_emubd_create(&cfg, &emubd_cfg);
  lfs_format(&lfs, &cfg);
  lfs_mount(&lfs, &cfg);
  set_lfs(&lfs);
}

static bool nop(void) {
  return true;
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  FuzzedDataProvider fuzzed_data(data, size);
  init_lfs();

  kv_init((kv_api_t){.lock = &nop, .unlock = &nop});

  while (fuzzed_data.remaining_bytes() > 0) {
    uint8_t choice = fuzzed_data.ConsumeIntegralInRange(0, 2);
    int key_len = fuzzed_data.ConsumeIntegralInRange(1, 256);
    int value_len = fuzzed_data.ConsumeIntegralInRange(1, 256);
    std::string key = fuzzed_data.ConsumeBytesAsString(key_len);
    std::vector<uint8_t> value = fuzzed_data.ConsumeBytes<uint8_t>(value_len);

    switch (choice) {
      case 0: {
        kv_set((const char*)key.data(), value.data(), value.size());
        break;
      }
      case 1: {
        uint8_t value_len = value.size();
        kv_get((const char*)key.data(), value.data(), &value_len);
        break;
      }
      default:
        break;
    }
  }

  lfs_emubd_destroy(&cfg);
  return 0;
}
