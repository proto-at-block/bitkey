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
  MCU_FLASH_STATUS_UNSUPPORTED = -77,  //!< Operation not supported.
} mcu_flash_status_t;

/**
 * @brief Public readback view of flash RDP levels.
 */
typedef enum {
  /**
   * @brief RDP level could not be decoded.
   */
  MCU_FLASH_RDP_UNKNOWN = 0,

  /**
   * @brief Device open.
   *
   * @note Secure and non-secure debug is supported.
   */
  MCU_FLASH_RDP_0,

  /**
   * @brief Device partially closed.
   *
   * @note Boot on SRAM is not permitted, debug is only allowed on non-secure
   * memory.
   */
  MCU_FLASH_RDP_0_5,

  /**
   * @brief Device memory protected.
   *
   * @note Boot address must target secure user flash memory. Access to
   * non-secure memory is not allowed when a debugger is attached.
   */
  MCU_FLASH_RDP_1,

  /**
   * @brief Device closed.
   *
   * @note Boot address must target user flash memory. Option bytes are
   * read only.
   */
  MCU_FLASH_RDP_2,
} mcu_flash_rdp_t;

/**
 * @brief Public readback view of flash option bytes.
 */
typedef struct {
  mcu_flash_rdp_t current_rdp;
} mcu_flash_opt_info_t;

/**
 * @brief Forward declaration (see MCU internal header for implementation).
 */
typedef struct mcu_flash_opt_t mcu_flash_opt_t;

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

/**
 * @brief Verifies that the option byte configuration specified by @p opt is
 * set.
 *
 * @param opt Option byte configuration.
 *
 * @return `true` if option bytes are set appropriately, otherwise `false`.
 *
 * @note On STM32U5 this verifies a strict, profile-specific register set for
 * development or production targets, except for the OPTR RDP field which is
 * set via a mfgtest command.
 */
bool mcu_flash_opt_verify(mcu_flash_opt_t const* opt);

/**
 * @brief Configures the option bytes of the target MCU flash controller.
 *
 * @param opt       Option byte configuration.
 * @param write_rdp When true, the RDP level from @p opt is programmed into
 *                  the OPTR register. When false, the current RDP level is
 *                  preserved. Callers should pass `false` for normal boot-time
 *                  option byte enforcement and `true` only when explicitly
 *                  locking a device to production RDP (e.g. via mfgtest).
 *
 * @return #MCU_FLASH_STATUS_OK on success, otherwise an error as defined in
 * #mcu_flash_status_t.
 *
 * @note On STM32U5 platforms, successfully programming option bytes may
 * trigger a system reset. In such successful cases, callers must not rely on
 * this function returning.
 */
mcu_flash_status_t mcu_flash_opt_write(mcu_flash_opt_t const* opt, bool write_rdp);

/**
 * @brief Reads the current flash option-byte state.
 *
 * @param[out] opt_info Populated with the current option-byte readback values.
 *
 * @return #MCU_FLASH_STATUS_OK on success, otherwise an error as defined in
 * #mcu_flash_status_t.
 */
mcu_flash_status_t mcu_flash_opt_read(mcu_flash_opt_info_t* opt_info);

/**
 * @brief Reads the configured target RDP level from an option-byte
 * configuration profile.
 *
 * @param[in] opt Option-byte configuration profile.
 * @param[out] rdp Populated with the configured target RDP level.
 *
 * @return #MCU_FLASH_STATUS_OK on success, otherwise an error as defined in
 * #mcu_flash_status_t.
 */
mcu_flash_status_t mcu_flash_opt_get_rdp(mcu_flash_opt_t const* opt, mcu_flash_rdp_t* rdp);
