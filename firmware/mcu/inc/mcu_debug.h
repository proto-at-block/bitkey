/**
 * @file
 *
 * @brief MCU Debug Module
 *
 * @{
 */

#pragma once

#include <stdbool.h>
#include <stdint.h>

/**
 * @brief Enables the Data and Watchpoint Trace (DWT) unit.
 */
void mcu_debug_dwt_enable(void);

/**
 * @brief Returns the current Data Watchpoint and Trace (DWT) counter value.
 *
 * @return DWT cycle counter.
 */
uint32_t mcu_debug_dwt_cycle_counter(void);

/**
 * @brief Returns `true` if the Debug Access Port (DAP) is configured for
 * debugging and a debugger is attached, otherwise `false`.
 *
 * @return `true` if debugger attached, otherwise `false`.
 */
bool mcu_debug_debugger_attached(void);

/**
 * @brief Issues a software-defined break.
 */
void mcu_debug_break(void);

/** @} */
