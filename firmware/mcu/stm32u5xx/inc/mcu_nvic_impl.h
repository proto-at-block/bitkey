/**
 * @file
 *
 * @{
 */

#pragma once

#include "mcu_dma.h"

/**
 * @brief Default IRQ priority for interrupts.
 *
 * @details The default NVIC IRQ priority must be less than or equal to the DMA
 * IRQ priority (logically `>=`).
 */
#define MCU_NVIC_DEFAULT_IRQ_PRIORITY (MCU_DMA_IRQ_PRIORITY + 1)

/**
 * @brief Highest priority IRQ.
 *
 * @note Should be reserved for IRQs that should interrupt DMA.
 */
#define MCU_NVIC_HIGH_IRQ_PRIORITY (MCU_DMA_IRQ_PRIORITY - 1)

/** @} */
