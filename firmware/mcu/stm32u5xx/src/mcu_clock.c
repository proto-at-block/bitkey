#include "mcu_clock.h"

#include "mcu_clock_impl.h"
#include "stm32u5xx_ll_bus.h"
#include "stm32u5xx_ll_icache.h"
#include "stm32u5xx_ll_pwr.h"
#include "stm32u5xx_ll_rcc.h"
#include "stm32u5xx_ll_system.h"
#include "stm32u5xx_ll_utils.h"

#include <stdbool.h>

// PLL1 configuration for system clock
typedef struct {
  bool use_hsi;        // true: HSI as source, false: HSE as source
  bool enable_bypass;  // true: HSE external clock, false: HSE crystal
  struct {
    uint32_t vco_input_range;  // Input frequency range selection
    uint32_t m;                // Input divider (PLL input = source / m)
    uint32_t n;                // VCO multiplier (VCO = PLL input * n)
    uint32_t r;                // SYSCLK divider (SYSCLK = VCO / r)
    uint32_t p;                // P output divider
    uint32_t q;                // Q output divider (for peripherals)
  } pll1;
} mcu_clock_config_t;

// PLL2 configuration for peripheral clocks
typedef struct {
  struct {
    uint32_t vco_input_range;  // Input frequency range selection
    uint32_t m;                // Input divider
    uint32_t n;                // VCO multiplier
    uint32_t p;                // P output divider
    uint32_t q;                // Q output divider (for OCTOSPI)
    uint32_t r;                // R output divider
  } pll2;
} aux_clock_config_t;

// Clock configuration table
static const mcu_clock_config_t mcu_clock_configs[] = {
  [MCU_CLOCK_HSE_16MHZ_CORE_160MHZ] =
    {
      .use_hsi = false,
      .enable_bypass = false,  // Using crystal, not external clock
      .pll1 =
        {
          .vco_input_range = LL_RCC_PLLINPUTRANGE_8_16,
          .m = 1,   // 16MHz / 1 = 16MHz PLL input
          .n = 20,  // 16MHz * 20 = 320MHz VCO
          .r = 2,   // 320MHz / 2 = 160MHz SYSCLK
          .p = 2,   // 320MHz / 2 = 160MHz P output
          .q = 4,   // 320MHz / 4 = 80MHz Q output
        },
    },
  [MCU_CLOCK_HSI_16MHZ_CORE_160MHZ] =
    {
      .use_hsi = true,
      .enable_bypass = false,  // Not applicable for HSI
      .pll1 =
        {
          .vco_input_range = LL_RCC_PLLINPUTRANGE_8_16,
          .m = 1,   // 16MHz / 1 = 16MHz PLL input
          .n = 20,  // 16MHz * 20 = 320MHz VCO
          .r = 2,   // 320MHz / 2 = 160MHz SYSCLK
          .p = 2,   // 320MHz / 2 = 160MHz P output
          .q = 4,   // 320MHz / 4 = 80MHz Q output
        },
    },
};

// Auxiliary clock configurations for PLL2
static const aux_clock_config_t aux_clock_configs[] = {
  [MCU_AUX_CLOCK_HSE_16MHZ_AUX_5MHZ] =
    {
      .pll2 =
        {
          .vco_input_range = LL_RCC_PLLINPUTRANGE_8_16,
          .m = 1,   // 16MHz / 1 = 16MHz PLL input
          .n = 10,  // 16MHz * 10 = 160MHz VCO
          .p = 8,   // 160MHz / 8 = 20MHz P output
          .q = 32,  // 160MHz / 32 = 5MHz Q output
          .r = 8,   // 160MHz / 8 = 20MHz R output
        },
    },
  [MCU_AUX_CLOCK_HSE_16MHZ_AUX_44MHZ] =
    {
      .pll2 =
        {
          .vco_input_range = LL_RCC_PLLINPUTRANGE_8_16,
          .m = 1,   // 16MHz / 1 = 16MHz PLL input
          .n = 22,  // 16MHz * 22 = 352MHz VCO
          .p = 8,   // 352MHz / 8 = 44MHz P output
          .q = 8,   // 352MHz / 8 = 44MHz Q output
          .r = 8,   // 352MHz / 8 = 44MHz R output
        },
    },
};

static void system_clock(const mcu_clock_config_t* config);
static void peripheral_common_clock(const aux_clock_config_t* config);
static void setup_systick(void);
static void mcu_clock_low_freq_enable(void);

uint32_t SystemCoreClock = MCU_CORE_CLOCK;

uint32_t mcu_clock_get_freq(void) {
  return SystemCoreClock;
}

void mcu_clock_init(const mcu_clock_config_e config_selection) {
  const mcu_clock_config_t* config = &mcu_clock_configs[config_selection];

  // Configure system clocks and power
  system_clock(config);

  // Configure low power clocks.
  mcu_clock_low_freq_enable();

  // Enable instruction cache for improved performance
  // ICACHE is always clocked on STM32U5, no need to enable clock
  ICACHE->CR &= ~ICACHE_CR_WAYSEL;  // 1-way cache (direct mapped)
  ICACHE->CR |= ICACHE_CR_EN;
  while ((ICACHE->SR & ICACHE_SR_BUSYF)) {
    // Wait for cache to be ready
  }

  // Configure SysTick for 1ms interrupts
  setup_systick();
}

void mcu_aux_clock_init(const mcu_aux_clock_config_e config_selection) {
  const aux_clock_config_t* config = &aux_clock_configs[config_selection];
  peripheral_common_clock(config);
}

// Configure system clock and power settings
static void system_clock(const mcu_clock_config_t* config) {
  // Enable PWR clock
  LL_AHB3_GRP1_EnableClock(LL_AHB3_GRP1_PERIPH_PWR);

  // Disable the internal Pull-Up in Dead Battery pins of UCPD peripheral
  PWR->UCPDR &= ~(PWR_UCPDR_UCPD_DBDIS);

  // Switch to SMPS regulator instead of LDO for better efficiency
  PWR->CR3 |= PWR_CR3_REGSEL;

  // Wait for SMPS to be ready
  while ((PWR->SVMSR & PWR_SVMSR_REGS) == 0) {
  }

  // Enable VddUSB for USB functionality
  PWR->SVMCR |= PWR_SVMCR_USV;

  // Set Flash latency for 160MHz operation (4 wait states)
  LL_FLASH_SetLatency(LL_FLASH_LATENCY_4);
  while (LL_FLASH_GetLatency() != LL_FLASH_LATENCY_4) {
  }

  // Configure voltage scaling to Scale 1 for 160MHz
  LL_PWR_SetRegulVoltageScaling(LL_PWR_REGU_VOLTAGE_SCALE1);
  while (LL_PWR_IsActiveFlag_VOS() == 0) {
  }

  // Select clock source
  if (config->use_hsi) {
    // Enable HSI
    LL_RCC_HSI_Enable();
    while (LL_RCC_HSI_IsReady() != 1) {
    }

    // Configure PLL1 using HSI
    RCC->PLL1CFGR &= ~RCC_PLL1CFGR_PLL1SRC;
    RCC->PLL1CFGR |= (0x2U << RCC_PLL1CFGR_PLL1SRC_Pos);  // HSI
  } else {
    // Configure HSE
    if (config->enable_bypass) {
      LL_RCC_HSE_EnableBypass();
    } else {
      LL_RCC_HSE_DisableBypass();
    }

    // Enable HSE oscillator
    LL_RCC_HSE_Enable();

    // Wait till HSE is ready
    while (LL_RCC_HSE_IsReady() != 1) {
      // Wait for HSE to stabilize
    }

    // Configure PLL1 using HSE
    RCC->PLL1CFGR &= ~RCC_PLL1CFGR_PLL1SRC;
    RCC->PLL1CFGR |= (0x2U << RCC_PLL1CFGR_PLL1SRC_Pos);  // HSE
  }

  // Enable EPOD booster for frequencies above 55MHz in voltage scale 1
  PWR->VOSR |= PWR_VOSR_BOOSTEN;

  // Wait for EPOD booster to be ready
  while ((PWR->VOSR & PWR_VOSR_BOOSTRDY) == 0) {
  }

  // Disable EPOD booster before configuring PLLMBOOST (required by STM32U5 hardware)
  PWR->VOSR &= ~PWR_VOSR_BOOSTEN;

  // Configure PLL1 M divider and MBOOST prescaler
  RCC->PLL1CFGR &= ~RCC_PLL1CFGR_PLL1M;
  RCC->PLL1CFGR |= ((config->pll1.m - 1) << RCC_PLL1CFGR_PLL1M_Pos);

  // Configure PLLMBOOST to DIV1 (value 0) - critical for correct PLL operation
  // PLLMBOOST must be DIV1 for 16MHz input to work correctly with our PLL settings
  RCC->PLL1CFGR &= ~RCC_PLL1CFGR_PLL1MBOOST;

  // Re-enable EPOD booster after PLLMBOOST configuration
  PWR->VOSR |= PWR_VOSR_BOOSTEN;

  // Wait for EPOD booster to be ready again
  while ((PWR->VOSR & PWR_VOSR_BOOSTRDY) == 0) {
  }

  // Configure PLL1 dividers N, P, Q, R
  RCC->PLL1DIVR = 0;
  RCC->PLL1DIVR |= ((config->pll1.n - 1) << RCC_PLL1DIVR_PLL1N_Pos);
  RCC->PLL1DIVR |= ((config->pll1.p - 1) << RCC_PLL1DIVR_PLL1P_Pos);
  RCC->PLL1DIVR |= ((config->pll1.q - 1) << RCC_PLL1DIVR_PLL1Q_Pos);
  RCC->PLL1DIVR |= ((config->pll1.r - 1) << RCC_PLL1DIVR_PLL1R_Pos);

  // Configure PLL1 input range
  RCC->PLL1CFGR &= ~RCC_PLL1CFGR_PLL1RGE;
  RCC->PLL1CFGR |= (config->pll1.vco_input_range << RCC_PLL1CFGR_PLL1RGE_Pos);

  // Disable PLL1 fractional mode (FRACEN = 0)
  RCC->PLL1CFGR &= ~RCC_PLL1CFGR_PLL1FRACEN;

  // Enable PLL1 outputs
  RCC->PLL1CFGR |= RCC_PLL1CFGR_PLL1PEN;
  RCC->PLL1CFGR |= RCC_PLL1CFGR_PLL1QEN;
  RCC->PLL1CFGR |= RCC_PLL1CFGR_PLL1REN;

  // Enable PLL1
  LL_RCC_PLL1_Enable();

  // Wait till PLL is ready
  while (LL_RCC_PLL1_IsReady() != 1) {
  }

  // Set AHB/APB prescalers before switching to PLL
  LL_RCC_SetAHBPrescaler(LL_RCC_SYSCLK_DIV_1);
  LL_RCC_SetAPB1Prescaler(LL_RCC_APB1_DIV_1);
  LL_RCC_SetAPB2Prescaler(LL_RCC_APB2_DIV_1);
  LL_RCC_SetAPB3Prescaler(LL_RCC_APB3_DIV_1);

  // Switch system clock to PLL1
  LL_RCC_SetSysClkSource(LL_RCC_SYS_CLKSOURCE_PLL1);

  // Wait till System clock is ready
  while (LL_RCC_GetSysClkSource() != LL_RCC_SYS_CLKSOURCE_STATUS_PLL1) {
  }

  // Update the system clock frequency variable
  LL_SetSystemCoreClock(MCU_CORE_CLOCK);

  // Calculate actual system clock frequency to verify configuration
  uint32_t pllm = ((RCC->PLL1CFGR & RCC_PLL1CFGR_PLL1M) >> RCC_PLL1CFGR_PLL1M_Pos) + 1;
  uint32_t pllmboost = ((RCC->PLL1CFGR & RCC_PLL1CFGR_PLL1MBOOST) >> RCC_PLL1CFGR_PLL1MBOOST_Pos);
  uint32_t plln = ((RCC->PLL1DIVR & RCC_PLL1DIVR_PLL1N) >> RCC_PLL1DIVR_PLL1N_Pos) + 1;
  uint32_t pllr = ((RCC->PLL1DIVR & RCC_PLL1DIVR_PLL1R) >> RCC_PLL1DIVR_PLL1R_Pos) + 1;

  // Calculate PLLMBOOST divider: 0=DIV1, 1=DIV2, 2=DIV4, 3=DIV8, 4=DIV16
  uint32_t pllmboost_div = (pllmboost == 0) ? 1 : (2U << (pllmboost - 1));

  // Source frequency is 16MHz (HSI or HSE) on STM32U5
  uint32_t source_freq = MCU_SOURCE_FREQ_HZ;

  // PLL input = Source / (PLL1M × PLLMBOOST)
  uint32_t pll_input_freq = source_freq / (pllm * pllmboost_div);
  uint32_t vco_freq = pll_input_freq * plln;
  uint32_t sysclk_freq = vco_freq / pllr;

  // Update SystemCoreClock with actual frequency
  SystemCoreClock = sysclk_freq;
}

// Configure PLL2 for peripheral clocks
static void peripheral_common_clock(const aux_clock_config_t* config) {
  // Configure PLL2 for OCTOSPI and other peripherals
  // Clear PLL2 source and set to HSE
  RCC->PLL2CFGR &= ~RCC_PLL2CFGR_PLL2SRC;
  RCC->PLL2CFGR |= (0x2U << RCC_PLL2CFGR_PLL2SRC_Pos);  // HSE

  // Configure PLL2 M divider
  RCC->PLL2CFGR &= ~RCC_PLL2CFGR_PLL2M;
  RCC->PLL2CFGR |= ((config->pll2.m - 1) << RCC_PLL2CFGR_PLL2M_Pos);

  // Configure PLL2 dividers N, P, Q, R
  RCC->PLL2DIVR = 0;
  RCC->PLL2DIVR |= ((config->pll2.n - 1) << RCC_PLL2DIVR_PLL2N_Pos);
  RCC->PLL2DIVR |= ((config->pll2.p - 1) << RCC_PLL2DIVR_PLL2P_Pos);
  RCC->PLL2DIVR |= ((config->pll2.q - 1) << RCC_PLL2DIVR_PLL2Q_Pos);
  RCC->PLL2DIVR |= ((config->pll2.r - 1) << RCC_PLL2DIVR_PLL2R_Pos);

  // Configure PLL2 input range
  RCC->PLL2CFGR &= ~RCC_PLL2CFGR_PLL2RGE;
  RCC->PLL2CFGR |= (config->pll2.vco_input_range << RCC_PLL2CFGR_PLL2RGE_Pos);

  // Enable PLL2 outputs
  RCC->PLL2CFGR |= RCC_PLL2CFGR_PLL2PEN;
  RCC->PLL2CFGR |= RCC_PLL2CFGR_PLL2QEN;
  RCC->PLL2CFGR |= RCC_PLL2CFGR_PLL2REN;

  // Enable PLL2
  LL_RCC_PLL2_Enable();

  // Wait till PLL2 is ready
  while (LL_RCC_PLL2_IsReady() != 1) {
  }

  // Select PLL2Q as OCTOSPI clock source
  LL_RCC_SetOCTOSPIClockSource(LL_RCC_OCTOSPI_CLKSOURCE_PLL2);
}

static void setup_systick(void) {
  // Configure SysTick for 1ms interrupts using actual SystemCoreClock value
  // SysTick_Config sets the reload value and enables SysTick with interrupt
  const uint32_t ticks_per_ms = SystemCoreClock / 1000U;

  // SysTick_Config expects the number of ticks between interrupts
  if (SysTick_Config(ticks_per_ms) != 0) {
    // SysTick configuration failed - value too large
    while (1) {
      // Error: SysTick reload value exceeds 24-bit maximum
    }
  }
}

static void mcu_clock_low_freq_enable(void) {
  /* Disable backup register protection. */
  LL_PWR_EnableBkUpAccess();

  /* Enable Low Speed External oscillator. */
  LL_RCC_LSE_EnableGlitchFilter();
  LL_RCC_LSE_Enable();

  while (!LL_RCC_LSE_IsReady()) {
    ;
  }

  LL_RCC_LSE_EnablePropagation();

  while (!LL_RCC_LSESYS_IsReady()) {
    ;
  }
}
