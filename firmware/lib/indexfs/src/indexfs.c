#include "indexfs.h"

#include "fwup.h"
#include "indexfs_impl.h"
#include "indexfs_monotonic.h"
#include "log.h"
#include "mcu_flash.h"

STATIC_VISIBLE_FOR_TESTING bool erase_flash(const uint32_t addr, const uint32_t size);
STATIC_VISIBLE_FOR_TESTING bool addr_in_range(const uint32_t addr, const uint32_t range_start,
                                              const uint32_t range_size);

indexfs_t* indexfs_create_static(indexfs_t* fs) {
  switch (fs->type) {
    case INDEXFS_TYPE_MONOTONIC: {
      // Check if the indexfs is valid
      if (!indexfs_monotonic_valid(fs)) {
        // Erase the flash if valid magic is not found
        if (!erase_flash((uint32_t)fs->address, fs->size)) {
          LOGE("indexfs create: erase fail");
          return NULL;
        }
      }

      // Initialize the indexfs
      if (!indexfs_monotonic_init(fs)) {
        LOGE("indexfs create: init fail");
        return NULL;
      }
      break;
    }
    default:
      LOGE("indexfs create: bad type %u", fs->type);
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
      LOGE("indexfs count: bad type %u", fs->type);
      return 0;
  }
}

bool indexfs_increment(indexfs_t* fs) {
  switch (fs->type) {
    case INDEXFS_TYPE_MONOTONIC: {
      return indexfs_monotonic_increment(fs);
    }
    default:
      LOGE("indexfs incr: bad type %u", fs->type);
      return false;
  }
}

bool indexfs_clear(indexfs_t* fs) {
  switch (fs->type) {
    case INDEXFS_TYPE_MONOTONIC: {
      return indexfs_monotonic_clear(fs);
    }
    default:
      LOGE("indexfs clear: bad type %u", fs->type);
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
      LOGE("indexfs get_flag: bad type %u", fs->type);
      return false;
  }
}

bool indexfs_set_flag(indexfs_t* fs, const uint8_t flag) {
  switch (fs->type) {
    case INDEXFS_TYPE_MONOTONIC: {
      return indexfs_monotonic_set_flag(fs, flag);
    }
    default:
      LOGE("indexfs set_flag: bad type %u", fs->type);
      return false;
  }
}

STATIC_VISIBLE_FOR_TESTING bool erase_flash(const uint32_t addr, const uint32_t size) {
  // Check alignments
  if (addr % MCU_FLASH_PAGE_SIZE != 0) {
    LOGE("Erase addr unaligned: 0x%08lX", addr);
    return false;
  }
  if (size % MCU_FLASH_PAGE_SIZE != 0) {
    LOGE("Erase size unaligned: %lu", size);
    return false;
  }

  // Check that the address range is not within the currently active slot
  if (addr_in_range(addr, (uint32_t)fwup_current_slot_address(), fwup_slot_size())) {
    LOGE("Erase in active slot");
    return false;
  }

  // Check that the address range is not within the bootloader slot
  if (addr_in_range(addr, (uint32_t)fwup_bl_address(), fwup_bl_size())) {
    LOGE("Erase in BL slot");
    return false;
  }

  const size_t pages = size / MCU_FLASH_PAGE_SIZE;
  for (uint32_t page = 0; page < pages; page++) {
    const uint32_t* page_address = (uint32_t*)((uint32_t)addr + (page * MCU_FLASH_PAGE_SIZE));
    const mcu_flash_status_t result = mcu_flash_erase_page((uint32_t*)page_address);

    if (result != MCU_FLASH_STATUS_OK) {
      LOGE("Flash erase err %i", result);
      return false;
    }
  }

  return true;
}

STATIC_VISIBLE_FOR_TESTING bool addr_in_range(const uint32_t addr, const uint32_t range_start,
                                              const uint32_t range_size) {
  if (addr >= range_start && (addr - range_start) < range_size) {
    return true;
  }
  return false;
}
