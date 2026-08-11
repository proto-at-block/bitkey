/**
 * @file rtos_mpu.h
 * @brief POSIX passthrough for rtos_mpu.h.
 *
 * The shared lib/rtos/inc/rtos_mpu.h header already provides non-embedded
 * (no-op) definitions for the privilege macros when EMBEDDED_BUILD is not
 * defined, and the POSIX FreeRTOS.h stub in this directory supplies the
 * FreeRTOS types it needs. This file only exists so that include paths which
 * put inc/posix first still resolve to the canonical header.
 */

#pragma once

#include "../rtos_mpu.h"
