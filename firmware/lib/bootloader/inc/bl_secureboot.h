/**
 * @file
 *
 * @brief Bootloader Secure Boot
 *
 * ## Overview
 *
 * This library provides functions for validating an application image
 * in the bootloader to implement secure boot.
 *
 * ## Usage
 *
 * ```
 * volatile secure_bool_t a_slot_valid = SECURE_FALSE;
 * volatile secure_bool_t b_slot_valid = SECURE_FALSE;
 *
 * a_slot_valid = bl_verify_app_slot(bl_cert, app_a_properties, app_a, app_a_size, app_a_signature);
 * b_slot_valid = bl_verify_app_slot(bl_cert, app_b_properties, app_b, app_b_size, app_b_signature);
 *
 * if ((a_slot_valid != SECURE_TRUE) && (b_slot_valid != SECURE_TRUE)) {
 *   mcu_reset_with_reason(MCU_RESET_INVALID_SIGNATURE);
 * }
 *
 * boot_slot_t slot_a = {
 *  .props = app_properties_a,
 *  .boot_addr = app_boot_a,
 *  .signature_verified = a_slot_valid,
 * };
 *
 * boot_slot_t slot_b = {
 *  .props = app_properties_b,
 *  .boot_addr = app_boot_b,
 *  .signature_verified = b_slot_valid,
 * };
 *
 * boot_slot_t* selected_slot = NULL;
 * volatile secure_bool_t slot_ok = SECURE_FALSE;
 * slot_ok = bl_select_slot(&slot_a, &slot_b, &selected_slot);
 * if (slot_ok != SECURE_TRUE) {
 *   mcu_reset_with_reason(MCU_RESET_INVALID_PROPERTIES);
 * }
 *
 * ASSERT(selected_slot != NULL);
 * ASSERT(!((selected_slot == &slot_a) ^ (a_slot_valid == SECURE_TRUE)));
 * ASSERT(!((selected_slot == &slot_b) ^ (b_slot_valid == SECURE_TRUE)));
 *
 * jump_to_app(selected_slot->boot_addr);
 * ```
 *
 * @{
 */

#pragma once

#if defined(BL_SECUREBOOT_APP_CERT)
#include "bl_secureboot_app_cert.h"
#endif
#if defined(BL_SECUREBOOT_PICOCERT)
#include "bl_secureboot_picocert.h"
#endif
#include "secutils.h"

#include <stdbool.h>
#include <stdint.h>

typedef struct {
  /**
   * @brief Pointer to the application properties specifying version
   * information for selecting the correct image to boot into.
   */
  app_properties_t* props;

  /**
   * @brief Start address of the application code in flash.
   *
   * @note This should be the start address of the executable code (the
   * vector table).
   */
  uintptr_t boot_addr;

  /**
   * @brief `true` if application signature verified by bootloader.
   */
  secure_bool_t signature_verified;
} boot_slot_t;

/**
 * @brief Verifies a slotted application image.
 *
 * @param bl_cert         Pointer to the bootloader certificate, used to
 *                        validate that the application certificate was signed
 *                        with the bootloader key.
 * @param app_properties  Pointer to the application properties.
 * @param app             Pointer to the start of the application image slot
 *                        in flash (start address of application metadata).
 * @param app_size        Total size of the application image in bytes,
 *                        excluding properties, metadata and signature.
 * @param app_signature   Pointer to the application signature, must be an ECDSA
 *                        signature of size #ECC_SIG_SIZE.
 *
 * @return #SECURE_TRUE if application image is valid, otherwise #SECURE_FALSE.
 */
secure_bool_t bl_verify_app_slot(app_certificate_t* bl_cert, app_properties_t* app_properties,
                                 uint8_t* app, uint32_t app_size, uint8_t* app_signature);

/**
 * @brief Selects the application image slot that should be booted into.
 *
 * @param[in]  slot_a         Pointer to the #boot_slot_t populated with the
 *                            slot A application image data.
 * @param[in]  slot_b         Pointer to the #boot_slot_t populated with the
 *                            slot B application image data.
 * @param[out] selected_slot  Output pointer to store the pointer to the
 *                            application image slot that should be booted into.
 *
 * @return #SECURE_TRUE if a valid slot image is present and @p selected_slot
 * was populated, otherwise #SECURE_FALSE. In the case of #SECURE_FALSE, no
 * application should be booted into.
 *
 * @note On success (#SECURE_TRUE returned), @p selected_slot will container a
 * pointer to either @p slot_a or @p slot_b.
 */
secure_bool_t bl_select_slot(boot_slot_t* slot_a, boot_slot_t* slot_b, boot_slot_t** selected_slot);

/**
 * @brief Verifies that an application certificate has been signed with the
 * bootloader key.
 *
 * @param app_cert  Pointer to the application certificate to verify.
 * @param bl_cert   Pointer to the bootloader certificate.
 *
 * @return #SECURE_TRUE if application certificate is valid, otherwise #SECURE_FALSE.
 */
secure_bool_t bl_verify_app_certificate(app_certificate_t* app_cert, app_certificate_t* bl_cert);

/**
 * @brief Verifies that an application has been signed with the application key.
 *
 * @param app_cert   Pointer to the application certificate.
 * @param app        Pointer to the start of the application image slot in flash
 *                   (start address of metadata).
 * @param app_size   Size of the application image in bytes, excluding properties,
 *                   metadata and signature.
 * @param signature  Pointer to the application signature.
 *
 * @return #SECURE_TRUE if application image is valid, otherwise #SECURE_FALSE.
 */
secure_bool_t bl_verify_application(app_certificate_t* app_cert, uint8_t* app, uint32_t app_size,
                                    uint8_t* signature);

/** @} */
