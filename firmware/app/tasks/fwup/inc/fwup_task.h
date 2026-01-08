/**
 * @file
 *
 * @brief Firmware Update Task
 *
 * @{
 */

#pragma once

#include <stdbool.h>

typedef struct {
  /**
   * @brief `true` if bootloader upgrade should be allowed.
   */
  bool bl_upgrade;
} fwup_task_options_t;

/**
 * @brief Creates the FWUP task thread.
 *
 * @param options Firmware update configuration options.
 */
void fwup_task_create(fwup_task_options_t options);

/** @} */
