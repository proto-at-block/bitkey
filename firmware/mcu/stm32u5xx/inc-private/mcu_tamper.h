/**
 * @file
 *
 * @brief MCU Tamper
 *
 * @{
 */

#pragma once

#include "stm32u5xx.h"

typedef enum {
  /**
   * @brief Backup domain voltage continuous monitoring.
   */
  MCU_TAMPER_FLAG_BD,

  /**
   * @brief Temperature monitoring.
   */
  MCU_TAMPER_FLAG_TEMP,

  /**
   * @brief Low Speed External Oscillator.
   */
  MCU_TAMPER_FLAG_LSE,

  /**
   * @brief RTC calendar overflow.
   */
  MCU_TAMPER_FLAG_RTC_OVF,

  /**
   * @brief JTAG/SWD access.
   */
  MCU_TAMPER_FLAG_JTAG,

  /**
   * @brief Monotonic counter overflow.
   */
  MCU_TAMPER_FLAG_MONOTONIC_CTR,

  /**
   * @brief Fault generation for cryptographic peripherals (SAES, PKA, AES, RNG).
   */
  MCU_TAMPER_FLAG_CRYPTO,
} mcu_tamper_flag_t;

/**
 * @brief Configures and clears all tampers.
 */
void mcu_tamper_init(void);

/**
 * @brief Clears the specified tamper flag.
 *
 * @param flag  Tamper flag to clear.
 */
void mcu_tamper_clear(mcu_tamper_flag_t flag);

/** @} */
