/**
 * @file
 *
 * @brief Firmware Update Addressing
 *
 * @{
 */

#pragma once

#include "attributes.h"

#include <stddef.h>
#include <stdint.h>

/**
 * @brief Retrieves the inactive firmware slot address for firmware update.
 *
 * @return Pointer to the start of flash for the inactive firmware slot.
 */
NO_OPTIMIZE void* fwup_target_slot_address(void);

/**
 * @brief Retrieves the inactive firmware slot's signature address.
 *
 * @return Pointer to the start of flash for the inactive slot signature.
 */
NO_OPTIMIZE void* fwup_target_slot_signature_address(void);

/**
 * @brief Returns the active firmware slot address.
 *
 * @return Pointer to the start of flash for the active firmware slot.
 */
NO_OPTIMIZE void* fwup_current_slot_address(void);

/**
 * @brief Retrieves the size of the firmware application slot.
 *
 * @return Size of application slot in bytes.
 *
 * @note Both the active and inactive slot are the same size. This is essential
 * for an A/B update scheme.
 */
NO_OPTIMIZE size_t fwup_slot_size(void);

/**
 * @brief Returns the address of the bootloader.
 *
 * @return Pointer to the start of flash for the bootloader image.
 */
NO_OPTIMIZE void* fwup_bl_address(void);

/**
 * @brief Retrieves the size of the bootloader image slot.
 *
 * @return Size of the bootloader slot in bytes.
 */
NO_OPTIMIZE size_t fwup_bl_size(void);

/** @} */
