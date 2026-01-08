#include "mcu_wdog.h"

#include "attributes.h"
#include "mcu_nvic.h"
#include "mcu_nvic_impl.h"
#include "mcu_reset.h"
#include "stm32u5xx_ll_iwdg.h"
#include "stm32u5xx_ll_rcc.h"
#include "stm32u5xx_ll_system.h"

#include <stdint.h>

/**
 * @brief Clock divider for the LSI to the IWDG.
 */
#define MCU_WDOG_PRESCALER LL_IWDG_PRESCALER_256

/**
 * @brief Target timeout of 8s assuming an LSI operating at 32 kHz.
 *
 * @details Independent watchdog timeout (ms) is given by the equation:
 *
 *     t_IWDG = (1 / f_LSI) * prescaler * (reload + 1)
 *
 * To generate the desired timeout (in milliseconds), one defines the reload
 * using the equation:
 *
 *     reload = t_IWDG / ((1 / f_LSI) * prescaler) - 1
 *
 * So for a timeout of `~8s`, we get:
 *
 *     reload = 8000 / ((1 / 32) * 256) - 1
 */
#define MCU_WDOG_RELOAD 999u

/**
 * @brief Target early wakeup around 7.75s assuming an LSI operating at 32 kHz.
 *
 * @details The independent watchdog triggers a hardware reset. To have
 * software catch and reset before the hardware reset, we have to set the early
 * wakeup interrupt. The time for the early wakeup interrupt is computed the
 * same as that for the reload value.
 */
#define MCU_WDOG_WKUP 967u

NO_OPTIMIZE void mcu_wdog_init(void) {
  /* Prevent IWDG reset when actively debugging. */
  LL_DBGMCU_APB1_GRP1_FreezePeriph(LL_DBGMCU_APB1_GRP1_IWDG_STOP);

  LL_RCC_LSI_Enable();

  /* Wait for LSI to stabilize. */
  while (!LL_RCC_LSI_IsReady()) {
    ;
  }

  /* Enable IWDG for register access. */
  LL_IWDG_Enable(IWDG);

  /* Enable write access to IWDG registers. */
  LL_IWDG_EnableWriteAccess(IWDG);

  /* Wait until prescaler and reload register updates complete. */
  while (LL_IWDG_IsActiveFlag_PVU(IWDG) || LL_IWDG_IsActiveFlag_RVU(IWDG)) {
    ;
  }

  LL_IWDG_SetPrescaler(IWDG, MCU_WDOG_PRESCALER);
  LL_IWDG_SetReloadCounter(IWDG, MCU_WDOG_RELOAD);

  /* Wait until EWI can be updated. */
  while (LL_IWDG_IsActiveFlag_EWU(IWDG)) {
    ;
  }

  /* Wakeup is down counter, so the delta of RELOAD - WKUP sets the IRQ time. */
  const uint16_t msk =
    ((MCU_WDOG_RELOAD - MCU_WDOG_WKUP) << IWDG_EWCR_EWIT_Pos) & IWDG_EWCR_EWIT_Msk;

  /* Write must be done in one shot, otherwise we must wait between access. */
  IWDG->EWCR = (msk | IWDG_EWCR_EWIE | IWDG_EWCR_EWIC);

  /* Wait for register writes to synchronize. */
  while (!LL_IWDG_IsReady(IWDG)) {
    ;
  }

  /* Disable write access to IWDG registers. */
  LL_IWDG_DisableWriteAccess(IWDG);

  /* Pet the watchdog. */
  LL_IWDG_ReloadCounter(IWDG);

  /* Enable the NVIC IRQ (higher priority). */
  mcu_nvic_set_priority(IWDG_IRQn, MCU_NVIC_HIGH_IRQ_PRIORITY);
  mcu_nvic_enable_irq(IWDG_IRQn);
}

void mcu_wdog_feed(void) {
  LL_IWDG_ReloadCounter(IWDG);
}

void IWDG_IRQHandler(void) {
  LL_IWDG_ClearFlag_EWIF(IWDG);
  mcu_reset_with_reason(MCU_RESET_WATCHDOG_TIMEOUT);
}
