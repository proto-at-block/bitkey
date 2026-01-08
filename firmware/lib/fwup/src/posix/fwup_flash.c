#include "attributes.h"
#include "fwup_flash_impl.h"
#include "fwup_impl.h"
#include "perf.h"

#include <stddef.h>
#include <stdint.h>
#include <string.h>

#define FLASH_PAGE_SIZE (0x00002000UL)

size_t app_slot_size = 632 * 1024;

size_t fwup_flash_get_max_chunk_size(void) {
  return sizeof(((fwpb_fwup_transfer_cmd_fwup_data_t*)0)->bytes);
}

bool fwup_flash_erase(perf_counter_t* UNUSED(perf), void* slot_addr, size_t size) {
  memset(slot_addr, 0xff, size);
  return true;
}

bool fwup_flash_erase_app(perf_counter_t* perf, void* slot_addr) {
  return fwup_flash_erase(perf, slot_addr, app_slot_size);
}

bool fwup_flash_erase_app_excluding_signature(perf_counter_t* perf, void* slot_addr) {
  return fwup_flash_erase(perf, slot_addr, (size_t)(app_slot_size)-FLASH_PAGE_SIZE);
}

bool fwup_flash_erase_app_signature(perf_counter_t* perf, void* signature_addr) {
  size_t page_base = (size_t)signature_addr & ~(FLASH_PAGE_SIZE - 1);
  return fwup_flash_erase(perf, (void*)page_base, FLASH_PAGE_SIZE);
}

bool fwup_flash_erase_bl(perf_counter_t* perf, void* slot_addr, size_t bl_size) {
  return fwup_flash_erase(perf, slot_addr, bl_size);
}

bool fwup_flash_write(perf_counter_t* UNUSED(perf), void* address, void const* data, size_t len) {
  memcpy(address, data, len);
  return true;
}
