/**
 * @file
 *
 * @brief Firmware Update Verification
 *
 * @{
 */

#pragma once

#include <stdint.h>

/**
 * @brief Verification results returned from the FWUP verify APIs.
 */
typedef enum {
  /**
   * @brief Success AF.
   *
   * @note The SUCCESS constant must have a large hamming distance from the other values.
   */
  FWUP_VERIFY_SUCCESS = 0x50CCE55AF,
  FWUP_VERIFY_SIGNATURE_INVALID = 1,  //<! Signature check failed.
  FWUP_VERIFY_VERSION_INVALID = 2,    //<! Invalid version in update metadata.
  FWUP_VERIFY_BAD_OFFSET = 3,         //<! Invalid offset for signature or application properties.
  FWUP_VERIFY_ERROR = 4,              //<! Generic error.
  FWUP_VERIFY_CANT_FIND_PROPERTIES = 5,  //<! Failed to find application properties.
  FWUP_VERIFY_FLASH_ERROR = 6,           //<! Failed to read/write/erase flash.
} fwup_verify_status_t;

/**
 * @brief Verifies a complete application image written to the target update
 * slot.
 *
 * @param app_properties_offset  Offset, in bytes, within the target application
 *                               slot to find the application properties.
 * @param signature_offset       Offset, in bytes, within the target application
 *                               slot to find the signature.
 *
 * @return #FWUP_VERIFY_SUCCESS on success, otherwise an error code as defined in
 * #fwup_verify_status_t.
 */
fwup_verify_status_t fwup_verify_new_app(uintptr_t app_properties_offset,
                                         uintptr_t signature_offset);

/**
 * @brief Verifies a complete bootloader image written to the target update
 * slot.
 *
 * @return #FWUP_VERIFY_SUCCESS on success, otherwise an error code as defined in
 * #fwup_verify_status_t.
 *
 * @note The bootloader update is staged within the target update slot.
 */
fwup_verify_status_t fwup_verify_new_bootloader(void);

/** @} */
