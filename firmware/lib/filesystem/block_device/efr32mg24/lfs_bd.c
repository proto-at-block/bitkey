#include "lfs_bd.h"

#include "assert.h"
#include "lfs.h"
#include "log.h"
#include "mcu_flash.h"
#include "perf.h"
#include "rtos_mutex.h"

extern char flash_filesystem_addr[];
extern char flash_filesystem_size[];
void* __attribute__((section(".flash_fs_section"))) fs_section;

#define FS_BLOCK_CYCLES   (500) /* Wear leveling cycles */
#define FS_LOOKAHEAD_SIZE (128) /* Must be a multiple of 8 */

static lfs_t SHARED_TASK_BSS lfs_handle;
static uint8_t SHARED_TASK_BSS lfs_read_buf[FLASH_PAGE_SIZE];
static uint8_t SHARED_TASK_BSS lfs_prog_buf[FLASH_PAGE_SIZE];
static uint8_t SHARED_TASK_BSS lfs_lookahead_buf[FS_LOOKAHEAD_SIZE];

static struct {
  perf_counter_t* read;
  perf_counter_t* write;
  perf_counter_t* erase;
} perf SHARED_TASK_DATA;

static bool is_flash_empty(void);
static uint32_t* block_to_addr(const lfs_block_t block, const lfs_off_t off);
static int bd_read(const struct lfs_config* cfg, lfs_block_t block, lfs_off_t off, void* buffer,
                   lfs_size_t size);
static int bd_write(const struct lfs_config* cfg, lfs_block_t block, lfs_off_t off,
                    const void* buffer, lfs_size_t size);
static int bd_erase(const struct lfs_config* cfg, lfs_block_t block);
static int bd_sync(const struct lfs_config* c);

static struct lfs_config SHARED_TASK_DATA config = {
  // Block device operations
  .read = bd_read,
  .prog = bd_write,
  .erase = bd_erase,
  .sync = bd_sync,

  // Block device configuration
  .read_size = FLASH_PAGE_SIZE,
  .prog_size = FLASH_PAGE_SIZE,
  .block_size = FLASH_PAGE_SIZE,
  .block_count = 0, /* Updated at runtime */
  .cache_size = FLASH_PAGE_SIZE,
  .lookahead_size = FS_LOOKAHEAD_SIZE,
  .block_cycles = FS_BLOCK_CYCLES,

  .read_buffer = lfs_read_buf,
  .prog_buffer = lfs_prog_buf,
  .lookahead_buffer = lfs_lookahead_buf,
};

lfs_t* bd_mount(void) {
  mcu_flash_init();

  /* Initialise perf counters */
  perf.read = perf_create(PERF_INTERVAL, bd_read);
  perf.write = perf_create(PERF_INTERVAL, bd_write);
  perf.erase = perf_create(PERF_INTERVAL, bd_erase);

  /* Set the block count based on the linker script */
  config.block_count = (size_t)flash_filesystem_size / FLASH_PAGE_SIZE;

  int status = 0;
  const bool empty = is_flash_empty();
  if (empty) {
    LOGI("Formatting filesystem");
    status = lfs_format(&lfs_handle, &config);
    if (status < 0) {
      LOGW("Error formatting filesystem");
      return NULL;
    }
  }

  status = lfs_mount(&lfs_handle, &config);
  if (status < 0) {
    return NULL;
  }

  return &lfs_handle;
}

int bd_erase_all(void) {
  int ret = 0;
  for (uint32_t block = 0; block < config.block_count; block++) {
    uint32_t* erase_addr = block_to_addr(block, 0);
    ret = mcu_flash_erase_page(erase_addr);
    if (ret < 0) {
      return ret;
    }
  }

  return ret;
}

static bool is_flash_empty(void) {
  uint32_t* base_address = (void*)flash_filesystem_addr;
  for (size_t offset = 0; offset < (size_t)flash_filesystem_size; offset += 4) {
    const uint32_t* address = (uint32_t*)((uint32_t)base_address + offset);
    const uint32_t data = *(address);
    if (data != 0xffffffff) {
      return false;
    }
  }

  return true;
}

static uint32_t* block_to_addr(const lfs_block_t block, const lfs_off_t off) {
  uint32_t* base_address = (void*)flash_filesystem_addr;

  ASSERT((uint32_t)block < (UINT32_MAX / config.block_size)); /* Check for multiply wrap */
  const uint32_t block_offset = block * config.block_size;

  const uint32_t offset = block_offset + off;
  ASSERT(offset >= block_offset); /* Check for addition wrap */

  ASSERT(UINT32_MAX - offset > (uint32_t)base_address); /* Check for addition wrap */
  return (uint32_t*)((uint32_t)base_address + offset);
}

static int bd_read(const struct lfs_config* cfg, lfs_block_t block, lfs_off_t off, void* buffer,
                   lfs_size_t size) {
  ASSERT(off % cfg->read_size == 0);
  ASSERT(size % cfg->read_size == 0);
  ASSERT(block <= cfg->block_count);
  if ((off % cfg->read_size != 0) || (size % cfg->read_size != 0) || (block > cfg->block_count)) {
    LOGE("bd_read invalid args");
    return -1;
  }

  perf_begin(perf.read);

  uint32_t* read_addr = block_to_addr(block, off);
  memcpy(buffer, read_addr, (size_t)size);

  perf_end(perf.read);

  return 0;
}

static int bd_write(const struct lfs_config* cfg, lfs_block_t block, lfs_off_t off,
                    const void* buffer, lfs_size_t size) {
  ASSERT(off % cfg->prog_size == 0);
  ASSERT(size % cfg->prog_size == 0);
  ASSERT(block <= cfg->block_count);

  if ((off % cfg->prog_size != 0) || (size % cfg->prog_size != 0) || (block > cfg->block_count)) {
    LOGE("bd_write invalid args");
    return -1;
  }

  perf_begin(perf.write);

  uint32_t* write_addr = block_to_addr(block, off);
  const mcu_flash_status_t result = mcu_flash_write_word(write_addr, buffer, size);

  perf_end(perf.write);

  return result;
}

static int bd_erase(const struct lfs_config* cfg, lfs_block_t block) {
  if (block > cfg->block_count) {
    LOGE("bd_erase invalid args");
    return -1;
  }

  perf_begin(perf.erase);

  uint32_t* erase_addr = block_to_addr(block, 0);
  const mcu_flash_status_t result = mcu_flash_erase_page(erase_addr);

  perf_end(perf.erase);
  return (int)result;
}

static int bd_sync(const struct lfs_config* cfg) {
  (void)cfg;
  return 0;
}

bool bd_error_str(char* buffer, const size_t len, const int error) {
  switch (error) {
    case MCU_FLASH_STATUS_OK:
      break;
    case MCU_FLASH_STATUS_INVALID_ADDR:
      snprintf(buffer, len, "Invalid flash address");
      break;
    case MCU_FLASH_STATUS_INVALID_LEN:
      snprintf(buffer, len, "Invalid length. Must be divisible by 4");
      break;
    case MCU_FLASH_STATUS_LOCKED:
      snprintf(buffer, len, "Flash address is locked");
      break;
    case MCU_FLASH_STATUS_TIMEOUT:
      snprintf(buffer, len, "Timeout while writing to flash");
      break;
    case MCU_FLASH_STATUS_UNALIGNED:
      snprintf(buffer, len, "Unaligned access to flash");
      break;
    default:
      return false;
  }

  return true;
}
