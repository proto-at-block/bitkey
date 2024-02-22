#include "fwup.h"
#include "fwup_impl.h"
#include "log.h"
#include "mcu_flash.h"

static bool fwup_flash_erase(perf_counter_t* perf, void* slot_addr, uint32_t size) {
  perf_begin(perf);

  const size_t pages = size / FLASH_PAGE_SIZE;
  LOGD("Erasing %u pages (%u bytes) starting at address: 0x%X", pages,
       (unsigned int)(pages * FLASH_PAGE_SIZE), (unsigned int)slot_addr);
  for (uint32_t page = 0; page < pages; page++) {
    const uint32_t* page_address = (uint32_t*)((uint32_t)slot_addr + (page * FLASH_PAGE_SIZE));

    // LOGD("Erasing page %lu @ %X", page, (unsigned int)page_address);
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
  return fwup_flash_erase(perf, slot_addr, (uint32_t)(fwup_slot_size()) - FLASH_PAGE_SIZE);
}

bool fwup_flash_erase_app_signature(perf_counter_t* perf, void* signature_addr) {
  uint32_t page_base = (uint32_t)signature_addr & ~(FLASH_PAGE_SIZE - 1);
  return fwup_flash_erase(perf, (void*)page_base, FLASH_PAGE_SIZE);
}

bool fwup_flash_erase_bl(perf_counter_t* perf, void* slot_addr, uint32_t bl_size) {
  return fwup_flash_erase(perf, slot_addr, bl_size);
}

bool fwup_flash_write(perf_counter_t* perf, void* address, void const* data, uint32_t len) {
  perf_begin(perf);
  const mcu_flash_status_t result = mcu_flash_write_word((uint32_t*)address, data, len);
  if (result != MCU_FLASH_STATUS_OK) {
    LOGE("Error %i erasing flash", result);
  }
  perf_end(perf);
  return (result == MCU_FLASH_STATUS_OK);
}
