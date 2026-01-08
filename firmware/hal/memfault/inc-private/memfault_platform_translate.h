/**
 * @file
 *
 * @details The functions defined in this header are MCU dependent and must be
 * brought up on each port.
 *
 * @{
 */

#pragma once

#include "memfault/ports/reboot_reason.h"

#include <stdint.h>

eMemfaultRebootReason memfault_translate_rmu_cause_to_memfault_enum(uint32_t reset_cause);

/** @} */
