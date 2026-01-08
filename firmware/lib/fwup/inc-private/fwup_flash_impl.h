/**
 * @file
 *
 * @brief Firmware Update Flash Operations
 *
 * @note Flash operations are contained in platform-specific sub-directories.
 *
 * @{
 */

#pragma once

#include "perf.h"
#include "wallet.pb.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/**
 * @brief Retrieves the maximum FWUP transfer chunk size.
 *
 * @return Maximum chunk size in bytes.
 */
size_t fwup_flash_get_max_chunk_size(void);

/**
 * @brief Erases the target application slot + signature.
 *
 * @param perf       Pointer to the flash erase performance counter.
 * @param slot_addr  Address in flash of the target application slot to erase.
 *
 * @return `true` if operation was successful, otherwise `false`.
 */
bool fwup_flash_erase_app(perf_counter_t* perf, void* slot_addr);

/**
 * @brief Erase the target app slot, but not the flash page containing the
 * signature.
 *
 * @param perf       Pointer to the flash erase performance counter.
 * @param slot_addr  Address in flash of the target application slot to erase.
 *
 * @return `true` if operation was successful, otherwise `false`.
 *
 * @note Used for delta FWUP.
 */
bool fwup_flash_erase_app_excluding_signature(perf_counter_t* perf, void* slot_addr);

/**
 * @brief Erase the target app slot's page containing the signature.
 *
 * @param perf       Pointer to the flash erase performance counter.
 * @param slot_addr  Address in flash of the target application slot whose
 *                   signature to erase.
 *
 * @return `true` if operation was successful, otherwise `false`.
 *
 * @note Used for delta FWUP.
 */
bool fwup_flash_erase_app_signature(perf_counter_t* perf, void* slot_addr);

/**
 * @brief Erases the bootloader.
 *
 * @param perf       Pointer to the flash erase performance counter.
 * @param slot_addr  Address in flash where the bootloader image resides.
 * @param bl_size    Size of the image slot in bytes.
 *
 * @return `true` if operation was successful, otherwise `false`.
 */
bool fwup_flash_erase_bl(perf_counter_t* perf, void* slot_addr, size_t bl_size);

/**
 * @brief Writes data to flash.
 *
 * @param perf      Pointer to the flash write performance counter.
 * @param address   Address in flash to write to.
 * @param data      Data to write to flash.
 * @param len       Length of @p data in bytes.
 *
 * @return `true` if operation was successful, otherwise `false`.
 */
bool fwup_flash_write(perf_counter_t* perf, void* address, void const* data, size_t len);

/** @} */
