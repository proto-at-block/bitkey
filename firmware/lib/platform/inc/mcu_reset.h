/**
 * @file mcu_reset.h
 * @brief POSIX passthrough for mcu_reset.h.
 *
 * The shared mcu/inc/mcu_reset.h header is hardware-independent (enum and
 * function declarations only), so the simulator uses it directly instead of
 * maintaining a drifting copy. Function implementations for POSIX live in
 * app/core-sim/src/posix/task_stubs.c.
 */

#pragma once

#include "../../../mcu/inc/mcu_reset.h"
