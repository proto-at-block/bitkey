#include "lfs_bd.h"

#include "assert.h"
#include "attributes.h"
#include "filesystem.h"
#include "lfs.h"
#include "lfs_bd_impl.h"
#include "log.h"
#include "mcu_flash.h"
#include "perf.h"

#include <limits.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>

extern char flash_filesystem_addr[];
extern char flash_filesystem_size[];
void* __attribute__((section(".flash_fs_section"))) fs_section;

#define LFS_READ_SIZE  FS_LFS_READ_SIZE
#define LFS_PROG_SIZE  FS_LFS_PROG_SIZE
#define LFS_CACHE_SIZE FS_FILE_CACHE_SIZE

static lfs_t SHARED_TASK_BSS lfs_handle;
static uint8_t SHARED_TASK_BSS lfs_read_buf[LFS_CACHE_SIZE];
static uint8_t SHARED_TASK_BSS lfs_prog_buf[LFS_CACHE_SIZE];
static uint8_t SHARED_TASK_BSS lfs_lookahead_buf[LFS_BD_LOOKAHEAD_SIZE];

/**
 * @brief Performance statistics for flash access.
 */
static struct {
  /**
   * @brief Performance counter for flash reads.
   */
  perf_counter_t* read;

  /**
   * @brief Performance counter for flash writes.
   */
  perf_counter_t* write;

  /**
   * @brief Performance counter for flash erases.
   */
  perf_counter_t* erase;
} perf SHARED_TASK_DATA;

/**
 * @brief Returns `true` if flash is empty.
 *
 * @details This is the case after a device has been programmed for the first
 * time. LFS will initialize flash by mounting the filesystem.
 *
 * @return `true` if flash is empty, otherwise `false`.
 */
static bool _bd_is_flash_empty(void);

/**
 * @brief Translates a block and offset within a block to the absolute address
 * in flash.
 *
 * @param block  LFS block ID.
 * @param off    Offset within the block.
 *
 * @return Absolute address in flash.
 */
static uint32_t* _bd_block_to_addr(const lfs_block_t block, const lfs_off_t off);

/**
 * @brief Reads @p size bytes into the passed @p buffer.
 *
 * @param[in]  cfg     Pointer to the LFS configuration.
 * @param[in]  block   LFS block ID.
 * @param[in]  off     Offset within the block to read from.
 * @param[out] buffer  Output buffer to read bytes into.
 * @param[in]  size    Length of the @p buffer in bytes.
 *
 * @return `0` on success, `< 0` on failure (see `mcu_flash.h`).
 */
static int _bd_read(const struct lfs_config* cfg, const lfs_block_t block, lfs_off_t off,
                    void* buffer, lfs_size_t size);

/**
 * @brief Writes bytes to the specified LFS block.
 *
 * @param cfg     Pointer to the LFS configuration.
 * @param block   LFS block ID.
 * @param off     Offset within the block to write to.
 * @param buffer  Data to write to flash.
 * @param size    Length of the @p buffer in bytes.
 *
 * @return `0` on success, `< 0` on failure (see `mcu_flash.h`).
 */
static int _bd_write(const struct lfs_config* cfg, const lfs_block_t block, lfs_off_t off,
                     const void* buffer, lfs_size_t size);

/**
 * @brief Erases the flash page corresponding to the given LFS block.
 *
 * @param cfg    Pointer to the LFS configuration.
 * @param block  LFS block ID.
 *
 * @return `0` on success, `< 0` on failure (see `mcu_flash.h`).
 */
static int _bd_erase(const struct lfs_config* cfg, const lfs_block_t block);

/**
 * @brief Syncs cached writes to flash.
 *
 * @param c  Pointer to the LFS configuration.
 *
 * @note This method is a no-op (no caching).
 */
static int _bd_sync(const struct lfs_config* c);

static struct lfs_config SHARED_TASK_DATA config = {
  // Block device operations
  .read = _bd_read,
  .prog = _bd_write,
  .erase = _bd_erase,
  .sync = _bd_sync,

  // Block device configuration
  .read_size = LFS_READ_SIZE,
  .prog_size = LFS_PROG_SIZE,
  .block_size = FLASH_PAGE_SIZE,
  .block_count = 0, /* Updated at runtime */
  .cache_size = LFS_CACHE_SIZE,
  .lookahead_size = LFS_BD_LOOKAHEAD_SIZE,
  .block_cycles = LFS_BD_BLOCK_CYCLES,

  .read_buffer = lfs_read_buf,
  .prog_buffer = lfs_prog_buf,
  .lookahead_buffer = lfs_lookahead_buf,
};

lfs_t* bd_mount(void) {
  mcu_flash_init();

  /* Initialise perf counters */
  perf.read = perf_create(PERF_INTERVAL, _bd_read);
  perf.write = perf_create(PERF_INTERVAL, _bd_write);
  perf.erase = perf_create(PERF_INTERVAL, _bd_erase);

  /* Set the block count based on the linker script */
  config.block_count = (size_t)flash_filesystem_size / FLASH_PAGE_SIZE;

  int status = 0;
  const bool empty = _bd_is_flash_empty();
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
  for (uintptr_t block = 0; block < config.block_count; block++) {
    uint32_t* erase_addr = _bd_block_to_addr(block, 0);
    ret = mcu_flash_erase_page(erase_addr);
    if (ret < 0) {
      return ret;
    }
  }

  return ret;
}

static bool _bd_is_flash_empty(void) {
  uintptr_t start = (uintptr_t)(void*)flash_filesystem_addr;
  const uintptr_t end = start + (size_t)(uintptr_t)flash_filesystem_size;

  /* Validate that start and end of FS is quad word aligned. */
  ASSERT((start % LFS_BD_MIN_WRITE_SIZE) == 0u);
  ASSERT((end % LFS_BD_MIN_WRITE_SIZE) == 0u);

  bool all_ff = true;
  while (all_ff && (start < end)) {
    all_ff =
      ((((const uint64_t*)start)[0] == UINT64_MAX) && (((const uint64_t*)start)[1] == UINT64_MAX));
    start += LFS_BD_MIN_WRITE_SIZE;
  }

  return all_ff;
}

static uint32_t* _bd_block_to_addr(const lfs_block_t block, const lfs_off_t off) {
  uintptr_t base_addr = (uintptr_t)(void*)flash_filesystem_addr;
  uintptr_t block_addr = (uintptr_t)block;

  /* Validate that multiplication will not wrap. */
  ASSERT(block_addr < (UINTPTR_MAX / config.block_size));

  /* Calculate in flash where the block starts. */
  const uintptr_t block_offset = block_addr * config.block_size;

  /* Calculate offset within the block. */
  const uintptr_t offset = block_offset + off;

  /* Validate that addition did not wrap. */
  ASSERT(offset >= block_offset);

  /* Validate that addition to base address will not wrap. */
  ASSERT((UINTPTR_MAX - offset) > base_addr);

  return (uint32_t*)(base_addr + offset);
}

static int _bd_read(const struct lfs_config* cfg, lfs_block_t block, lfs_off_t off, void* buffer,
                    lfs_size_t size) {
  ASSERT(off % cfg->read_size == 0);
  ASSERT(size % cfg->read_size == 0);
  ASSERT(block < cfg->block_count);
  if ((off % cfg->read_size != 0) || (size % cfg->read_size != 0) || (block >= cfg->block_count)) {
    LOGE("bd_read invalid args");
    return -1;
  }

  perf_begin(perf.read);

  uint32_t* read_addr = _bd_block_to_addr(block, off);
  memcpy(buffer, read_addr, (size_t)size);

  perf_end(perf.read);

  return 0;
}

static int _bd_write(const struct lfs_config* cfg, lfs_block_t block, lfs_off_t off,
                     const void* buffer, lfs_size_t size) {
  ASSERT(off % cfg->prog_size == 0);
  ASSERT(size % cfg->prog_size == 0);
  ASSERT(block < cfg->block_count);

  if ((off % cfg->prog_size != 0) || (size % cfg->prog_size != 0) || (block >= cfg->block_count)) {
    LOGE("bd_write invalid args");
    return -1;
  }

  perf_begin(perf.write);

  uint32_t* write_addr = _bd_block_to_addr(block, off);
  const mcu_flash_status_t result = mcu_flash_write_word(write_addr, buffer, size);

  perf_end(perf.write);

  return result;
}

static int _bd_erase(const struct lfs_config* cfg, lfs_block_t block) {
  if (block >= cfg->block_count) {
    LOGE("bd_erase invalid args");
    return -1;
  }

  perf_begin(perf.erase);

  uint32_t* erase_addr = _bd_block_to_addr(block, 0);
  const mcu_flash_status_t result = mcu_flash_erase_page(erase_addr);

  perf_end(perf.erase);
  return (int)result;
}

static int _bd_sync(const struct lfs_config* cfg) {
  /* This method is a no-op as we do not cache flash writes. */
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

    case MCU_FLASH_STATUS_PROG_ERROR:
      snprintf(buffer, len, "Flash programming error");
      break;

    case MCU_FLASH_STATUS_OPT_ERROR:
      snprintf(buffer, len, "Failure programming option bytes");
      break;

    default:
      return false;
  }

  return true;
}
