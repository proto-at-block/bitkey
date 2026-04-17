/**
 * @file
 *
 * @brief MCU Option Bytes
 *
 * @{
 */

#pragma once

#include "mcu_flash.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/**
 * @brief Option byte configuration.
 */
typedef enum {
  MCU_FLASH_OPT_PROFILE_DEV = 0,
  MCU_FLASH_OPT_PROFILE_PROD,
} mcu_flash_opt_profile_t;

struct mcu_flash_opt_t {
  /**
   * @brief Option-byte profile selector used for strict verification.
   */
  mcu_flash_opt_profile_t profile;

  /**
   * @brief Target RDP level for this option-byte profile.
   */
  mcu_flash_rdp_t target_rdp;

  /**
   * @brief Boot entry address on reset.
   */
  uintptr_t bootloader_address;

  /**
   * @brief Size of the bootloader in bytes.
   */
  size_t bootloader_size;

  /**
   * @brief Lock the bootloader.
   */
  bool bootloader_lock;

  /**
   * @brief Lock boot entry address.
   */
  bool boot_address_lock;
};

/** @} */
