/**
 * @file sysinfo_task.h
 *
 * @brief System Information Task
 *
 * @{
 */

#pragma once

#include "platform.h"

#include <stdbool.h>

/**
 * @brief Creates the system information task.
 */
void sysinfo_task_create(const platform_hwrev_t hwrev);

/** @} */
