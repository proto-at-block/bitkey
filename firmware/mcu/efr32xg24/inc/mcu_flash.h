#pragma once

#include "attributes.h"
#include "mcu.h"

#define MCU_FLASH_PAGE_SIZE FLASH_PAGE_SIZE

/* These enums may inter-mix with filesystem (aka. littlefs) return codes.
 * Therefore all non-OK enums are offset by -70 to not clash with any other return codes */
typedef enum {
  MCU_FLASH_STATUS_OK = 0,             /* Flash write/erase successful */
  MCU_FLASH_STATUS_INVALID_ADDR = -70, /* Invalid address. Write to an address that is not Flash. */
  MCU_FLASH_STATUS_INVALID_LEN = -71,  /* Invalid length. Must be divisible by 4. */
  MCU_FLASH_STATUS_LOCKED = -72,       /* Flash address is locked */
  MCU_FLASH_STATUS_TIMEOUT = -73,      /* Timeout while writing to Flash */
  MCU_FLASH_STATUS_UNALIGNED = -74,    /* Unaligned access to Flash */
} mcu_flash_status_t;

void mcu_flash_init(void);
RAMFUNC mcu_flash_status_t mcu_flash_write_word(uint32_t* address, void const* data, uint32_t len);
RAMFUNC mcu_flash_status_t mcu_flash_erase_page(uint32_t* address);
