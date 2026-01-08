#include "assert.h"
#include "fwup_addr.h"
#include "fwup_flash_impl.h"
#include "fwup_impl.h"
#include "log.h"
#include "mcu_flash.h"
#include "perf.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

size_t fwup_flash_get_max_chunk_size(void) {
  size_t max_chunk_size = sizeof(((fwpb_fwup_transfer_cmd_fwup_data_t*)0)->bytes);
  while (max_chunk_size && ((max_chunk_size % MCU_FLASH_WRITE_ALIGNMENT) != 0u)) {
    max_chunk_size--;
  }
  return max_chunk_size;
}

bool fwup_flash_erase(perf_counter_t* perf, void* slot_addr, size_t size) {
  perf_begin(perf);

  const size_t pages = size / FLASH_PAGE_SIZE;
  LOGD("Erasing %zu pages (%u bytes) starting at address: 0x%X", pages,
       (unsigned int)(pages * FLASH_PAGE_SIZE), (unsigned int)slot_addr);
  for (size_t page = 0; page < pages; page++) {
    const uint32_t* page_address = (uint32_t*)((uintptr_t)slot_addr + (page * FLASH_PAGE_SIZE));

    const mcu_flash_status_t result = mcu_flash_erase_page((uint32_t*)page_address);

    if (result != MCU_FLASH_STATUS_OK) {
      LOGE("Error %i erasing flash", result);
      goto error;
    }
  }

  perf_end(perf);
  return true;

error:
  perf_end(perf);
  return false;
}

bool fwup_flash_erase_app(perf_counter_t* perf, void* slot_addr) {
  return fwup_flash_erase(perf, slot_addr, fwup_slot_size());
}

bool fwup_flash_erase_app_excluding_signature(perf_counter_t* perf, void* slot_addr) {
  return fwup_flash_erase(perf, slot_addr, fwup_slot_size() - FLASH_PAGE_SIZE);
}

bool fwup_flash_erase_app_signature(perf_counter_t* perf, void* signature_addr) {
  uintptr_t page_base = (uintptr_t)signature_addr & ~(FLASH_PAGE_SIZE - 1);
  return fwup_flash_erase(perf, (void*)page_base, FLASH_PAGE_SIZE);
}

bool fwup_flash_erase_bl(perf_counter_t* perf, void* slot_addr, size_t bl_size) {
  return fwup_flash_erase(perf, slot_addr, bl_size);
}

bool fwup_flash_write(perf_counter_t* perf, void* address, void const* data, size_t len) {
  perf_begin(perf);
  const mcu_flash_status_t result = mcu_flash_write_word((uint32_t*)address, data, len);
  if (result != MCU_FLASH_STATUS_OK) {
    LOGE("Error %i writing flash", result);
  }
  perf_end(perf);
  return (result == MCU_FLASH_STATUS_OK);
}
