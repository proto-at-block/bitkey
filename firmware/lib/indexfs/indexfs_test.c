#include <criterion/criterion.h>

#include <stdint.h>

#ifndef FLASH_PAGE_SIZE
#define FLASH_PAGE_SIZE (0x00002000UL)
#endif

#include "indexfs.h"
#include "indexfs_impl.h"
#include "mcu_flash.h"

static uintptr_t test_current_slot_address = 0x10000;
static uintptr_t test_bootloader_address = 0x20000;
static size_t test_slot_size = FLASH_PAGE_SIZE;
static size_t test_bootloader_size = FLASH_PAGE_SIZE;
static mcu_flash_status_t test_erase_status = MCU_FLASH_STATUS_OK;
static uint32_t test_erase_page_call_count = 0;

bool rtos_in_isr(void) {
  return false;
}

bool indexfs_monotonic_init(indexfs_t* UNUSED(fs)) {
  return true;
}

bool indexfs_monotonic_valid(indexfs_t* UNUSED(fs)) {
  return true;
}

uint16_t indexfs_monotonic_count(indexfs_t* UNUSED(fs)) {
  return 0;
}

bool indexfs_monotonic_increment(indexfs_t* UNUSED(fs)) {
  return true;
}

bool indexfs_monotonic_clear(indexfs_t* UNUSED(fs)) {
  return true;
}

uint8_t indexfs_monotonic_get_flag(indexfs_t* UNUSED(fs)) {
  return 0;
}

bool indexfs_monotonic_set_flag(indexfs_t* UNUSED(fs), const uint8_t UNUSED(flag)) {
  return true;
}

void* fwup_current_slot_address(void) {
  return (void*)test_current_slot_address;
}

size_t fwup_slot_size(void) {
  return test_slot_size;
}

void* fwup_bl_address(void) {
  return (void*)test_bootloader_address;
}

size_t fwup_bl_size(void) {
  return test_bootloader_size;
}

mcu_flash_status_t mcu_flash_erase_page(uint32_t* UNUSED(address)) {
  test_erase_page_call_count++;
  return test_erase_status;
}

static void setup(void) {
  test_current_slot_address = 0x10000;
  test_bootloader_address = 0x20000;
  test_slot_size = FLASH_PAGE_SIZE;
  test_bootloader_size = FLASH_PAGE_SIZE;
  test_erase_status = MCU_FLASH_STATUS_OK;
  test_erase_page_call_count = 0;
}

TestSuite(indexfs, .init = setup);

Test(indexfs, addr_in_range_includes_range_start) {
  const uint32_t range_start = 0x40000;
  const uint32_t range_size = FLASH_PAGE_SIZE;

  cr_assert(addr_in_range(range_start, range_start, range_size));
}

Test(indexfs, addr_in_range_handles_range_end_overflow) {
  const uint32_t range_start = UINT32_MAX - 0x10;
  const uint32_t range_size = 0x20;
  const uint32_t addr = UINT32_MAX - 0x8;

  cr_assert(addr_in_range(addr, range_start, range_size));
}

Test(indexfs, erase_flash_rejects_active_slot_base_address) {
  const bool erased = erase_flash((uint32_t)test_current_slot_address, FLASH_PAGE_SIZE);

  cr_assert_not(erased);
  cr_assert_eq(test_erase_page_call_count, 0);
}

Test(indexfs, erase_flash_rejects_bootloader_base_address) {
  const bool erased = erase_flash((uint32_t)test_bootloader_address, FLASH_PAGE_SIZE);

  cr_assert_not(erased);
  cr_assert_eq(test_erase_page_call_count, 0);
}

Test(indexfs, erase_flash_allows_address_at_end_of_protected_range) {
  const uint32_t addr = (uint32_t)(test_current_slot_address + test_slot_size);
  const bool erased = erase_flash(addr, FLASH_PAGE_SIZE);

  cr_assert(erased);
  cr_assert_eq(test_erase_page_call_count, 1);
}
