/**
 * @file
 *
 * @brief Coprocessor Control
 *
 * @{
 */

#pragma once

#include "mcu_gpio.h"

#include <stdbool.h>
#include <stdint.h>

/**
 * @brief Status codes returned by #coproc_get_status().
 */
typedef enum {
  /**
   * @brief Status GPIO not available.
   */
  COPROC_STATUS_NOT_AVAIL = 0,

  /**
   * @brief Co-processor has booted.
   */
  COPROC_STATUS_RUNNING,

  /**
   * @brief Co-processor has not booted.
   */
  COPROC_STATUS_OFF,
} coproc_status_t;

typedef struct {
  /**
   * @brief Co-processor power configuration.
   */
  struct {
    /**
     * @brief Reset line to the co-processor.
     */
    struct {
      /**
       * @brief Output GPIO used to toggle the coprocessor's reset line.
       *
       * @note Set to `NULL` if not available.
       */
      const mcu_gpio_config_t* gpio;
    } reset;

    /**
     * @brief Co-processor power enable.
     */
    struct {
      /**
       * @brief Output GPIO used to assert or de-assert the co-processor's
       * power.
       *
       * @note Set to `NULL` if not available.
       */
      const mcu_gpio_config_t* gpio;
    } en;

    /**
     * @brief Boot status.
     */
    struct {
      /**
       * @brief Input GPIO used to read the co-processor's boot status.
       *
       * @note Set to `NULL` if not available.
       */
      const mcu_gpio_config_t* gpio;
    } boot;

    /**
     * @brief Timeout (in milliseconds) for the co-processor to power on from
     * off.
     */
    uint32_t power_on_timeout_ms;

    /**
     * @brief Duration (in milliseconds) for the co-processor to power off from
     * on.
     */
    uint32_t power_off_duration_ms;
  } power;
} coproc_cfg_t;

/**
 * @brief Initializes the co-processor library.
 *
 * @note Should be called before other API methods.
 */
void coproc_init(const coproc_cfg_t* cfg);

/**
 * @brief Returns the boot status of the co-processor.
 *
 * @return Co-processor running status.
 */
coproc_status_t coproc_get_status(void);

/** @} */
