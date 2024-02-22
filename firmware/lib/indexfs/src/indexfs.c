#include "indexfs.h"

#include "fwup.h"
#include "indexfs_monotonic.h"
#include "log.h"
#include "mcu_flash.h"

static bool erase_flash(const uint32_t addr, const uint32_t size);
static bool addr_in_range(const uint32_t addr, const uint32_t range_start,
                          const uint32_t range_size);

indexfs_t* indexfs_create_static(indexfs_t* fs) {
  switch (fs->type) {
    case INDEXFS_TYPE_MONOTONIC: {
      // Check if the indexfs is valid
      if (!indexfs_monotonic_valid(fs)) {
        // Erase the flash if valid magic is not found
        if (!erase_flash((uint32_t)fs->address, fs->size)) {
          LOGE("indexfs_create_static(): failed to erase flash");
          return NULL;
        }
      }

      // Initialize the indexfs
      if (!indexfs_monotonic_init(fs)) {
        LOGE("indexfs_create_static(): failed to initialize monotonic indexfs");
        return NULL;
      }
      break;
    }
    default:
      LOGE("indexfs_create_static(): unknown type: %u", fs->type);
      return NULL;
  }

  return fs;
}

uint32_t indexfs_count(indexfs_t* fs) {
  switch (fs->type) {
    case INDEXFS_TYPE_MONOTONIC: {
      return indexfs_monotonic_count(fs);
    }
    default:
      LOGE("indexfs_count(): unknown type: %u", fs->type);
      return 0;
  }
}

bool indexfs_increment(indexfs_t* fs) {
  switch (fs->type) {
    case INDEXFS_TYPE_MONOTONIC: {
      return indexfs_monotonic_increment(fs);
    }
    default:
      LOGE("indexfs_increment(): unknown type: %u", fs->type);
      return false;
  }
}

bool indexfs_clear(indexfs_t* fs) {
  switch (fs->type) {
    case INDEXFS_TYPE_MONOTONIC: {
      return indexfs_monotonic_clear(fs);
    }
    default:
      LOGE("indexfs_clear(): unknown type: %u", fs->type);
      return false;
  }
}

bool indexfs_get_flag(indexfs_t* fs, uint8_t* flag) {
  switch (fs->type) {
    case INDEXFS_TYPE_MONOTONIC: {
      *flag = indexfs_monotonic_get_flag(fs);
      return true;
    }
    default:
      LOGE("indexfs_get_flag(): unknown type: %u", fs->type);
      return false;
  }
}

bool indexfs_set_flag(indexfs_t* fs, const uint8_t flag) {
  switch (fs->type) {
    case INDEXFS_TYPE_MONOTONIC: {
      return indexfs_monotonic_set_flag(fs, flag);
    }
    default:
      LOGE("indexfs_set_flag(): unknown type: %u", fs->type);
      return false;
  }
}

static bool erase_flash(const uint32_t addr, const uint32_t size) {
  // Check alignments
  if (addr % MCU_FLASH_PAGE_SIZE != 0) {
    LOGE("erase_flash(): addr is not aligned: 0x%08lX", addr);
    return false;
  }
  if (size % MCU_FLASH_PAGE_SIZE != 0) {
    LOGE("erase_flash(): size is not aligned: %lu", size);
    return false;
  }

  // Check that the address range is not within the currently active slot
  if (addr_in_range(addr, (uint32_t)fwup_current_slot_address(), fwup_slot_size())) {
    LOGE("erase_flash(): address: 0x%08lX is within the currently active slot 0x%08lX, %lu", addr,
         (uint32_t)fwup_current_slot_address(), fwup_slot_size());
    return false;
  }

  // Check that the address range is not within the bootloader slot
  if (addr_in_range(addr, (uint32_t)fwup_bl_address(), fwup_bl_size())) {
    LOGE("erase_flash(): address: 0x%08lX is within the bootloader slot", addr);
    return false;
  }

  const size_t pages = size / MCU_FLASH_PAGE_SIZE;
  LOGD("Erasing %u pages (%u bytes) starting at address: 0x%X", pages,
       (unsigned int)(pages * MCU_FLASH_PAGE_SIZE), (unsigned int)addr);

  for (uint32_t page = 0; page < pages; page++) {
    const uint32_t* page_address = (uint32_t*)((uint32_t)addr + (page * MCU_FLASH_PAGE_SIZE));
    const mcu_flash_status_t result = mcu_flash_erase_page((uint32_t*)page_address);

    if (result != MCU_FLASH_STATUS_OK) {
      LOGE("Error %i erasing flash", result);
      return false;
    }
  }

  return true;
}

static bool addr_in_range(const uint32_t addr, const uint32_t range_start,
                          const uint32_t range_size) {
  if (addr > range_start && addr < range_start + range_size) {
    return true;
  }
  return false;
}
