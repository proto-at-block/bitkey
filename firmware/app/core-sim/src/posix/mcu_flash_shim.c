/**
 * @file mcu_flash_shim.c
 * @brief POSIX MCU flash shim for core-sim
 *
 * Provides memcpy-based implementations of mcu_flash_write_word and
 * mcu_flash_erase_page for POSIX builds. This allows real firmware code
 * (like indexfs) to work without modification on the simulator.
 *
 * The "flash" is actually RAM buffers (e.g., fwup_addr.c slot buffers),
 * so writes are just memcpy operations.
 */

#include "mcu_flash.h"

#include <string.h>

mcu_flash_status_t mcu_flash_write_word(uint32_t* address, void const* data, uint32_t len) {
  memcpy(address, data, len);
  return MCU_FLASH_STATUS_OK;
}

mcu_flash_status_t mcu_flash_erase_page(uint32_t* address) {
  // Erase by setting to 0xFF (flash erased state)
  memset(address, 0xFF, MCU_FLASH_PAGE_SIZE);
  return MCU_FLASH_STATUS_OK;
}

void mcu_flash_init(void) {
  // No-op on POSIX
}
