#include "bio_flash_storage.h"

#include "assert.h"
#include "attributes.h"
#include "mcu_flash.h"

#include <string.h>

// Linker-provided symbols for the bio_flash partition.
// Weak so that platforms without the partition (e.g. W3-UXC) link cleanly.
extern uint8_t flash_bio_addr[] __attribute__((weak));
extern uint32_t flash_bio_size __attribute__((weak));

// Cached flag indicating whether a template exists in bio_flash.
// Stored in shared BSS so all tasks can read it without needing
// MPU access to the bio_flash partition. Updated on save, erase,
// and init. Initialized from flash during bio_flash_storage_init()
// before any other task checks onboarding.
static bool SHARED_TASK_BSS bio_flash_template_cached;

typedef struct {
  uint32_t magic;
  uint32_t version;
  uint32_t size;
  uint32_t crc32;
} bio_flash_header_t;

_Static_assert(sizeof(bio_flash_header_t) == BIO_FLASH_HEADER_SIZE, "header size mismatch");

// Compile-time check that the expected max template size fits in the partition.
// The partition must hold the header plus the full template.
_Static_assert(BIO_FLASH_MAX_TEMPLATE_SIZE + sizeof(bio_flash_header_t) <= BIO_FLASH_PARTITION_SIZE,
               "BIO_FLASH_MAX_TEMPLATE_SIZE exceeds bio_flash partition capacity");

static uint32_t compute_crc32(const uint8_t* data, uint32_t len) {
  uint32_t crc = 0xFFFFFFFF;
  for (uint32_t i = 0; i < len; i++) {
    crc ^= data[i];
    for (int k = 0; k < 8; k++) {
      crc = (crc & 1) ? (crc >> 1) ^ 0xEDB88320 : crc >> 1;
    }
  }
  return ~crc;
}

static void* bio_flash_base_ptr;
static uint32_t bio_flash_capacity_val;

static void* flash_base(void) {
  return bio_flash_base_ptr;
}

static uint32_t flash_capacity(void) {
  return bio_flash_capacity_val;
}

static uint32_t max_template_size(void) {
  uint32_t cap = flash_capacity();
  if (cap <= sizeof(bio_flash_header_t)) {
    return 0;
  }
  return cap - sizeof(bio_flash_header_t);
}

static bool erase_all_pages(void) {
  uint32_t capacity = flash_capacity();
  uint8_t* base = flash_base();

  for (uint32_t offset = 0; offset < capacity; offset += MCU_FLASH_PAGE_SIZE) {
    mcu_flash_status_t status = mcu_flash_erase_page((uint32_t*)(base + offset));
    if (status != MCU_FLASH_STATUS_OK) {
      return false;
    }
  }
  return true;
}

bool bio_flash_storage_save(const uint8_t* data, uint32_t size) {
  if (bio_flash_base_ptr == NULL) {
    return false;
  }

  if (data == NULL || size == 0) {
    return false;
  }

  if (size > max_template_size()) {
    return false;
  }

  if (!erase_all_pages()) {
    return false;
  }

  // Flash is now blank — clear cached flag. It will be set back to true
  // only if the full write (data + header) succeeds below.
  bio_flash_template_cached = false;

  uint8_t* base = flash_base();

  // Write template data first, header last. This ensures the header (which
  // gates existence checks) is only committed after the payload is fully written.
  uint32_t align_mask = MCU_FLASH_WRITE_ALIGNMENT - 1;
  uint32_t aligned_size = size & ~align_mask;
  uint32_t remainder = size - aligned_size;
  ASSERT(remainder < MCU_FLASH_WRITE_ALIGNMENT);

  if (aligned_size != 0) {
    mcu_flash_status_t status =
      mcu_flash_write_word((uint32_t*)(base + sizeof(bio_flash_header_t)), data, aligned_size);
    if (status != MCU_FLASH_STATUS_OK) {
      return false;
    }
  }

  // Write any remaining bytes with zero-padding to write alignment.
  if (remainder != 0) {
    uint8_t tail[MCU_FLASH_WRITE_ALIGNMENT] = {0};
    memcpy(tail, data + aligned_size, remainder);
    mcu_flash_status_t status = mcu_flash_write_word(
      (uint32_t*)(base + sizeof(bio_flash_header_t) + aligned_size), tail, sizeof(tail));
    if (status != MCU_FLASH_STATUS_OK) {
      return false;
    }
  }

  // Commit the header last so exists/get_size only see valid data.
  bio_flash_header_t header = {
    .magic = BIO_FLASH_MAGIC,
    .version = BIO_FLASH_HEADER_VERSION,
    .size = size,
    .crc32 = compute_crc32(data, size),
  };
  mcu_flash_status_t status = mcu_flash_write_word((uint32_t*)base, &header, sizeof(header));
  if (status != MCU_FLASH_STATUS_OK) {
    return false;
  }

  bio_flash_template_cached = true;
  return true;
}

bool bio_flash_storage_read(uint8_t* data, uint32_t* size_out) {
  if (bio_flash_base_ptr == NULL) {
    return false;
  }

  if (data == NULL || size_out == NULL) {
    return false;
  }

  void* base = flash_base();

  bio_flash_header_t header;
  memcpy(&header, base, sizeof(header));

  if (header.magic != BIO_FLASH_MAGIC) {
    return false;
  }

  if (header.size == 0 || header.size > max_template_size()) {
    return false;
  }

  memcpy(data, (uint8_t*)base + sizeof(bio_flash_header_t), header.size);

  uint32_t crc = compute_crc32(data, header.size);
  if (crc != header.crc32) {
    return false;
  }

  *size_out = header.size;
  return true;
}

bool bio_flash_storage_erase(void) {
  if (bio_flash_base_ptr == NULL) {
    return true;  // No partition to erase; treat as success.
  }
  if (!erase_all_pages()) {
    return false;
  }
  bio_flash_template_cached = false;
  return true;
}

bool bio_flash_storage_exists(void) {
  if (bio_flash_base_ptr == NULL) {
    return false;
  }
  void* base = flash_base();
  bio_flash_header_t header;
  memcpy(&header, base, sizeof(header));
  if ((header.magic != BIO_FLASH_MAGIC) || (header.size == 0) ||
      (header.size > max_template_size())) {
    return false;
  }
  // Validate CRC to guard against partial header writes (e.g. power loss).
  uint32_t crc = compute_crc32((const uint8_t*)base + sizeof(header), header.size);
  return crc == header.crc32;
}

uint32_t bio_flash_storage_max_size(void) {
  return max_template_size();
}

void bio_flash_storage_init(void) {
  if (flash_bio_addr != NULL) {
    bio_flash_base_ptr = (void*)flash_bio_addr;
    bio_flash_capacity_val = (uint32_t)(uintptr_t)&flash_bio_size;
  }
  bio_flash_template_cached = bio_flash_storage_exists();
}

void bio_flash_storage_set_flash(void* base, uint32_t capacity) {
  bio_flash_base_ptr = base;
  bio_flash_capacity_val = capacity;
}

bool bio_flash_storage_template_exists(void) {
  return bio_flash_template_cached;
}

bool bio_flash_storage_get_size(uint32_t* size_out) {
  if (bio_flash_base_ptr == NULL || size_out == NULL) {
    return false;
  }
  bio_flash_header_t header;
  memcpy(&header, flash_base(), sizeof(header));
  if ((header.magic != BIO_FLASH_MAGIC) || (header.size == 0) ||
      (header.size > max_template_size())) {
    return false;
  }
  *size_out = header.size;
  return true;
}

bool bio_flash_storage_check_capacity(uint32_t fpc_max_template_size) {
  if (BIO_FLASH_MAX_TEMPLATE_SIZE != fpc_max_template_size) {
    return false;
  }
  if (BIO_FLASH_MAX_TEMPLATE_SIZE > max_template_size()) {
    return false;
  }
  return true;
}
