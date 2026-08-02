/**
 * @file power.h
 * @brief POSIX passthrough for power.h.
 *
 * The canonical hal/power header only needs the shared mcu_gpio/exti type
 * declarations, which have POSIX impl headers (mcu/posix/inc). POSIX no-op
 * implementations of the referenced functions live in
 * app/core-sim/src/posix/task_stubs.c.
 */

#pragma once

#include "../../../hal/power/inc/power.h"
