#pragma once

#include "attributes.h"
#include "mcu.h"

#define MCU_FLASH_PAGE_SIZE FLASH_PAGE_SIZE

/**
 * @brief Status codes returned by the MCU flash API functions.
 *
 * @details These enums may inter-mix with filesystem (aka. littlefs) return
 * codes. Therefore all non-OK enums are offset by `-70` to not clash with any
 * other return codes.
 */
typedef enum {
  MCU_FLASH_STATUS_OK = 0,              //!< Flash write/erase successful
  MCU_FLASH_STATUS_INVALID_ADDR = -70,  //!< Invalid address. Write to an address that is not Flash.
  MCU_FLASH_STATUS_INVALID_LEN = -71,  //!< Invalid length. Must be divisible by minimum write size.
  MCU_FLASH_STATUS_LOCKED = -72,       //!< Flash address is locked
  MCU_FLASH_STATUS_TIMEOUT = -73,      //!< Timeout while writing to Flash
  MCU_FLASH_STATUS_UNALIGNED = -74,    //!< Unaligned access to Flash
  MCU_FLASH_STATUS_PROG_ERROR = -75,   //!< Error programming Flash
  MCU_FLASH_STATUS_OPT_ERROR = -76,    //!< Error programming option bytes
} mcu_flash_status_t;

/**
 * @brief Initializes the flash module.
 */
void mcu_flash_init(void);

/**
 * @brief Writes bytes to flash starting at the given @p address in flash.
 *
 * @param address  Address to start writing to.
 * @param data     Address to start writing from.
 * @param len      Number of bytes to write.
 *
 * @return #MCU_FLASH_STATUS_OK on success, otherwise a status code as defined
 * in #mcu_flash_status_t.
 *
 * @note @p data and @p len must be aligned depending on the minimum write size for the target.
 */
RAMFUNC mcu_flash_status_t mcu_flash_write_word(uint32_t* address, void const* data, uint32_t len);

/**
 * @brief Erase the page of flash starting at the given @p address.
 *
 * @param address Start of the flash page.
 *
 * @return #MCU_FLASH_STATUS_OK on success, otherwise a status code as defined
 * in #mcu_flash_status_t. Note: the given @p address must be page aligned.
 */
RAMFUNC mcu_flash_status_t mcu_flash_erase_page(uint32_t* address);
