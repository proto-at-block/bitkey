#include "indexfs_monotonic.h"

#include "arithmetic.h"
#include "log.h"
#include "mcu_flash.h"

static const uint8_t INDEXFS_MONOTONIC_ENTRY_MAGIC = 0x5A;
static const uint8_t CLEAR_TRUE = 0x0;
static const uint8_t CLEAR_FALSE = 0xF;
static const uint8_t FLAGS_EMPTY = 0xF;

typedef struct PACKED {
  uint8_t magic;
  uint16_t count;
  uint8_t clear : 4;
  uint8_t flags : 4;
} indexfs_monotonic_entry_t;

static const uint32_t INDEXFS_MONOTONIC_MAGIC[] = {
  0x49584653,  // IXFS
  0x4d4f4e4f,  // MONO
  0x56455231,  // VER1
};
static const uint32_t magic_offset = ARRAY_SIZE(INDEXFS_MONOTONIC_MAGIC);

static bool magic_exists(uint32_t* address);
static bool write_magic(uint32_t* address);
static uint32_t current_index(indexfs_t* fs);

bool indexfs_monotonic_init(indexfs_t* fs) {
  ASSERT(fs->type == INDEXFS_TYPE_MONOTONIC);

  // compare first two words of fs->address against the indexfs_monotonic magic
  if (!magic_exists(fs->address)) {
    // write magic to first two words of fs->address
    return write_magic(fs->address);
  }

  return true;
}

bool indexfs_monotonic_valid(indexfs_t* fs) {
  ASSERT(fs->type == INDEXFS_TYPE_MONOTONIC);

  return magic_exists(fs->address);
}

uint16_t indexfs_monotonic_count(indexfs_t* fs) {
  ASSERT(fs->type == INDEXFS_TYPE_MONOTONIC);

  uint16_t count = 0;
  for (uint32_t i = magic_offset; i < fs->size; i++) {
    indexfs_monotonic_entry_t* entry = (indexfs_monotonic_entry_t*)(fs->address + i);
    if (entry->magic == INDEXFS_MONOTONIC_ENTRY_MAGIC) {
      if (entry->clear == CLEAR_FALSE) {
        // Uncleared entry
        count = entry->count;
      } else if (entry->clear == CLEAR_TRUE) {
        // Cleared entry
        count = 0;
      } else {
        ASSERT(true);
      }
    } else {
      // No more entries
      break;
    }
  }

  return count;
}

bool indexfs_monotonic_increment(indexfs_t* fs) {
  ASSERT(fs->type == INDEXFS_TYPE_MONOTONIC);

  const uint16_t count = indexfs_monotonic_count(fs);
  if (count + 1 > UINT16_MAX) {
    LOGE("indexfs_monotonic_increment(): %s: count overflow", fs->name);
    return false;
  }

  const uint32_t index = current_index(fs);
  const uint32_t write_pointer = magic_offset + index;

  indexfs_monotonic_entry_t entry = {
    .magic = INDEXFS_MONOTONIC_ENTRY_MAGIC,
    .count = count + 1,
    .clear = CLEAR_FALSE,
    .flags = FLAGS_EMPTY,
  };

  mcu_flash_status_t result = mcu_flash_write_word(fs->address + write_pointer, (uint32_t*)&entry,
                                                   sizeof(indexfs_monotonic_entry_t));
  if (result != MCU_FLASH_STATUS_OK) {
    LOGE("Error %i writing flash", result);
    return false;
  }

  return true;
}

bool indexfs_monotonic_clear(indexfs_t* fs) {
  ASSERT(fs->type == INDEXFS_TYPE_MONOTONIC);

  const uint16_t count = indexfs_monotonic_count(fs);
  if (count == 0) {
    LOGE("indexfs_monotonic_clear(): %s: no count to clear", fs->name);
    return false;
  }

  const uint32_t index = current_index(fs);
  const uint32_t write_pointer = magic_offset + index;

  indexfs_monotonic_entry_t* entry = (indexfs_monotonic_entry_t*)(fs->address + write_pointer);
  indexfs_monotonic_entry_t new_entry = {
    .magic = INDEXFS_MONOTONIC_ENTRY_MAGIC,
    .count = count,
    .clear = CLEAR_TRUE,
    .flags = entry->flags,
  };

  mcu_flash_status_t result = mcu_flash_write_word(
    fs->address + write_pointer, (uint32_t*)&new_entry, sizeof(indexfs_monotonic_entry_t));
  if (result != MCU_FLASH_STATUS_OK) {
    LOGE("Error %i writing flash", result);
    return false;
  }

  return true;
}

static bool magic_exists(uint32_t* address) {
  return *address == INDEXFS_MONOTONIC_MAGIC[0] && *(address + 1) == INDEXFS_MONOTONIC_MAGIC[1];
}

static bool write_magic(uint32_t* address) {
  // write the magic to the first two words of fs->address
  mcu_flash_status_t result = mcu_flash_write_word(
    address, INDEXFS_MONOTONIC_MAGIC, ARRAY_SIZE(INDEXFS_MONOTONIC_MAGIC) * sizeof(uint32_t));
  if (result != MCU_FLASH_STATUS_OK) {
    LOGE("Error %i writing flash", result);
    return false;
  }

  return true;
}

static uint32_t current_index(indexfs_t* fs) {
  ASSERT(fs->type == INDEXFS_TYPE_MONOTONIC);

  for (uint32_t i = magic_offset; i < fs->size; i++) {
    indexfs_monotonic_entry_t* entry = (indexfs_monotonic_entry_t*)(fs->address + i);
    if (entry->magic != INDEXFS_MONOTONIC_ENTRY_MAGIC) {
      return i - magic_offset;
    }
  }

  return 0;
}

uint8_t indexfs_monotonic_get_flag(indexfs_t* fs) {
  ASSERT(fs->type == INDEXFS_TYPE_MONOTONIC);

  // Get the latest entry and return it's flag value
  const uint32_t index = current_index(fs);
  if (index < 1) {
    return 0;
  }

  uint32_t write_pointer = magic_offset + index - 1;
  indexfs_monotonic_entry_t* entry = (indexfs_monotonic_entry_t*)(fs->address + write_pointer);

  return entry->flags;
}

bool indexfs_monotonic_set_flag(indexfs_t* fs, const uint8_t flag) {
  ASSERT(fs->type == INDEXFS_TYPE_MONOTONIC);

  if (flag > 0x0f) {
    LOGE("indexfs_monotonic_set_flag(): %s: invalid flag: %x", fs->name, flag);
    return false;
  }

  const uint16_t count = indexfs_monotonic_count(fs);
  if (count == 0) {
    LOGE("indexfs_monotonic_set_flag(): %s: no count to set flags on", fs->name);
    return false;
  }

  const uint32_t index = current_index(fs);
  const uint32_t write_pointer = magic_offset + index;

  // TODO: check that the flag can be written (only 1->0 transitions allowed)

  indexfs_monotonic_entry_t* entry = (indexfs_monotonic_entry_t*)(fs->address + write_pointer);
  indexfs_monotonic_entry_t new_entry = {
    .magic = INDEXFS_MONOTONIC_ENTRY_MAGIC,
    .count = count,
    .clear = entry->clear,
    .flags = flag & 0x0f,
  };

  mcu_flash_status_t result = mcu_flash_write_word(
    fs->address + write_pointer, (uint32_t*)&new_entry, sizeof(indexfs_monotonic_entry_t));
  if (result != MCU_FLASH_STATUS_OK) {
    LOGE("Error %i writing flash", result);
    return false;
  }

  return true;
}
