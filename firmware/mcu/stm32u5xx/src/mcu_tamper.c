#include "mcu_tamper.h"

#include "attributes.h"
#include "stm32u5xx.h"
#include "stm32u5xx_ll_bus.h"
#include "stm32u5xx_ll_pwr.h"
#include "stm32u5xx_ll_rcc.h"

#include <stdint.h>

void mcu_tamper_init(void) {
  /* Enable clock to Tamper (bundled with RTC). */
  LL_APB3_GRP1_EnableClock(LL_APB3_GRP1_PERIPH_RTCAPB);
  LL_RCC_SetRTCClockSource(LL_RCC_RTC_CLKSOURCE_LSE);
  LL_RCC_EnableRTC();

  /* Disable all tampers. */
  TAMP->CR1 = 0;
  TAMP->CR2 = 0;

  /* Clear all tampers. */
  TAMP->SCR = (TAMP_SCR_CTAMP1F | TAMP_SCR_CTAMP2F | TAMP_SCR_CTAMP3F | TAMP_SCR_CTAMP4F |
               TAMP_SCR_CTAMP5F | TAMP_SCR_CTAMP6F | TAMP_SCR_CTAMP7F | TAMP_SCR_CTAMP8F |
               TAMP_SCR_CITAMP1F | TAMP_SCR_CITAMP2F | TAMP_SCR_CITAMP3F | TAMP_SCR_CITAMP5F |
               TAMP_SCR_CITAMP6F | TAMP_SCR_CITAMP7F | TAMP_SCR_CITAMP8F | TAMP_SCR_CITAMP9F |
               TAMP_SCR_CITAMP11F | TAMP_SCR_CITAMP12F | TAMP_SCR_CITAMP13F);
}

void mcu_tamper_clear(mcu_tamper_flag_t flag) {
  uint32_t mask;

  switch (flag) {
    case MCU_TAMPER_FLAG_BD:
      mask = TAMP_SCR_CITAMP1F;
      break;

    case MCU_TAMPER_FLAG_TEMP:
      mask = TAMP_SCR_CITAMP2F;
      break;

    case MCU_TAMPER_FLAG_LSE:
      mask = TAMP_SCR_CITAMP3F;
      break;

    case MCU_TAMPER_FLAG_RTC_OVF:
      mask = TAMP_SCR_CITAMP5F;
      break;

    case MCU_TAMPER_FLAG_JTAG:
      mask = TAMP_SCR_CITAMP6F;
      break;

    case MCU_TAMPER_FLAG_MONOTONIC_CTR:
      mask = TAMP_SCR_CITAMP8F;
      break;

    case MCU_TAMPER_FLAG_CRYPTO:
      mask = TAMP_SCR_CITAMP9F;
      break;

    default:
      return;
  }

  TAMP->SCR = mask;
}
