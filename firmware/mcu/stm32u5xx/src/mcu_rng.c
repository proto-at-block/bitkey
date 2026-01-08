#include "mcu_rng.h"

#include "assert.h"
#include "stm32u5xx.h"
#include "stm32u5xx_ll_bus.h"
#include "stm32u5xx_ll_rng.h"

#include <stdint.h>

/**
 * @brief NIST compliance is based on a 48 MHz RNG clock.
 */
#define _MCU_RNG_NISTC_CLK_HZ 48000000

/**
 * @brief NIST SP800-90B compliant register value per AN-4230.
 */
#define _MCU_RNG_NISTC_CR 0x00F00D00u

/**
 * @brief Control register configuration for the RNG.
 *
 * @details Configuration is as follows:
 *   - NISTC   = 0x00 (Non-custom configuration, NIST compliant).
 *   - CLKDIV  = 0x00 (No clock divider per AN-4230).
 *   - CONFIG1 = 0x0F (Configuration 1 value per AN-4230).
 *   - CONFIG2 = 0x00 (Configuration 2 value per AN-4230).
 *   - CONFIG3 = 0x0D (Configuration 3 value per AN-4230).
 *   - CED     = 0x00 (Clock error detection disabled)
 *   - ARDIS   = 0x01 (Auto reset of RNG on error disabled)
 */
#define _MCU_RNG_CR_MSK                                           \
  (((0x00u << RNG_CR_NISTC_Pos) & RNG_CR_NISTC_Msk) |             \
   ((0x00u << RNG_CR_CLKDIV_Pos) & RNG_CR_CLKDIV_Msk) |           \
   ((0x0Fu << RNG_CR_RNG_CONFIG1_Pos) & RNG_CR_RNG_CONFIG1_Msk) | \
   ((0x00u << RNG_CR_RNG_CONFIG2_Pos) & RNG_CR_RNG_CONFIG2_Msk) | \
   ((0x0Du << RNG_CR_RNG_CONFIG3_Pos) & RNG_CR_RNG_CONFIG3_Msk) | \
   ((0x00u << RNG_CR_CED_Pos) & RNG_CR_CED_Msk) |                 \
   ((0x01u << RNG_CR_ARDIS_Pos) & RNG_CR_ARDIS_Msk))

/**
 * @brief HTCR value per AN-4230.
 */
#define _MCU_RNG_HTCR 0x0000A2B0

/**
 * @brief NSCR value per AN-4230.
 */
#define _MCU_RNG_NSCR 0x00017CBBu

/**
 * @brief Configures or re-configures the RNG.
 */
static void mcu_rng_configure(void);

void mcu_rng_init(void) {
  /* Enable the clock to the RNG */
  LL_AHB2_GRP1_EnableClock(LL_AHB2_GRP1_PERIPH_RNG);

  /* Reset the RNG module. */
  LL_AHB2_GRP1_ForceReset(LL_AHB2_GRP1_PERIPH_RNG);
  LL_AHB2_GRP1_ReleaseReset(LL_AHB2_GRP1_PERIPH_RNG);

  mcu_rng_configure();
}

uint32_t mcu_rng_get(void) {
  /* Check for RNG seed or clock errors. */
  if ((RNG->SR & RNG_SR_SECS) != 0u) {
    /* Attempt to recover. */
    mcu_rng_configure();
  }

  /* Wait for data to be available. */
  do {
    /* Assert if recovery failed. */
    ASSERT((RNG->SR & RNG_SR_SECS) == 0u);
  } while ((RNG->SR & RNG_SR_DRDY) == 0u);

  return RNG->DR;
}

static void mcu_rng_configure(void) {
  /* Program the CR, must Hold the RNG in reset to configure. */
  RNG->CR = ((0x01u << RNG_CR_CONDRST_Pos) & RNG_CR_CONDRST_Msk);
  RNG->CR |= _MCU_RNG_CR_MSK;

  /* Validate CR value is NISTC compliant. */
  ASSERT((RNG->CR & ~(RNG_CR_ARDIS | RNG_CR_CONDRST | RNG_CR_CLKDIV | RNG_CR_CED)) ==
         _MCU_RNG_NISTC_CR);

  RNG->HTCR = _MCU_RNG_HTCR;
  RNG->NSCR = _MCU_RNG_NSCR;

  /* Enable the RNG module. */
  RNG->CR |= RNG_CR_RNGEN;

  /* Bring the RNG module out of reset. */
  RNG->CR &= ~RNG_CR_CONDRST;
}
