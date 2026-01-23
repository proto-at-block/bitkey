#pragma once

#include "mcu_clock_impl.h"

#include <stdint.h>

// Clock frequencies for STM32U5
#define MCU_CORE_CLOCK     160000000U  // 160MHz
#define MCU_SOURCE_FREQ_HZ 16000000U   // Both HSE and HSI are 16MHz

// System clock configuration options (PLL1)
typedef enum {
  MCU_CLOCK_HSE_16MHZ_CORE_160MHZ,  // HSE 16MHz crystal -> 160MHz system clock
  MCU_CLOCK_HSI_16MHZ_CORE_160MHZ,  // HSI 16MHz internal -> 160MHz system clock
} mcu_clock_config_e;

// Auxiliary clock configuration options (PLL2) for peripherals
typedef enum {
  MCU_AUX_CLOCK_HSE_16MHZ_AUX_5MHZ,   // HSE 16MHz -> 5MHz (low speed OCTOSPI)
  MCU_AUX_CLOCK_HSE_16MHZ_AUX_44MHZ,  // HSE 16MHz -> 44MHz (high speed OCTOSPI)
} mcu_aux_clock_config_e;

// Initialize system clock with selected configuration
void mcu_clock_init(const mcu_clock_config_e config_selection);

// Initialize auxiliary peripheral clocks (PLL2)
void mcu_aux_clock_init(const mcu_aux_clock_config_e config_selection);
