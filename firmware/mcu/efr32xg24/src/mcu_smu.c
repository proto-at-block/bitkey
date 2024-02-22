#include "mcu_smu.h"

#include "attributes.h"
#include "mcu_reset.h"

#include "em_smu.h"

NO_OPTIMIZE void SMU_PRIVILEGED_IRQHandler(void) {
  /* based off of section 4.5.3 of
   * https://www.silabs.com/documents/public/application-notes/an1374-trustzone.pdf
   */
  uint32_t faulting_peripheral_id = SMU->PPUFS;  // tied to reference manual peripheral map

  (void)faulting_peripheral_id;

  mcu_reset_with_reason(MCU_RESET_FAULT);
}

void mcu_smu_init(void) {
  /* based off of section 4.1.1 of:
   * https://www.silabs.com/documents/public/application-notes/an1374-trustzone.pdf
   * Except for the EFR32xG21 devices, all Series 2 devices enable the SMU clock in CMU before
   * programming the SMU registers. */
#if (_SILICON_LABS_32B_SERIES_2_CONFIG > 1)
  CMU->CLKEN1_SET = CMU_CLKEN1_SMU;
#endif

  /* unlock SMU for modifications */
  SMU->LOCK = SMU_LOCK_SMULOCKKEY_UNLOCK;

  /* initialize SMU with whitelist of peripherals */
  SMU_Init_TypeDef smu_init;
  smu_init.enable = true;
  smu_init.ppu.reg[0] = 0xffffffff;  // disable all peripherals by default
  smu_init.ppu.reg[1] = 0xffffffff;  // disable all peripherals by default

  /* allowed peripherals for unprivileged acccess
   * based on: https://docs.silabs.com/gecko-platform/4.1/emlib/api/efr32xg21/group-smu
   */
  smu_init.ppu.access.privilegedTIMER0 = false;
  smu_init.ppu.access.privilegedTIMER1 = false;
  smu_init.ppu.access.privilegedGPIO = false;
  smu_init.ppu.access.privilegedCMU = false;
  smu_init.ppu.access.privilegedMSC = false;
  smu_init.ppu.access.privilegedI2C1 = false;
  smu_init.ppu.access.privilegedEUSART1 = false;
  smu_init.ppu.access.privilegedLDMA = false;
  smu_init.ppu.access.privilegedLDMAXBAR = false;
  smu_init.ppu.access.privilegedSEMAILBOX = false;
  SMU_Init(&smu_init);

  /* lock SMU from further modifications */
  SMU->LOCK = SMU_LOCK_SMULOCKKEY_DEFAULT;

  /* Clear and enable the SMU PPUPRIV and PPUINST interrupts
   * based off of section 4.5.3 of
   * https://www.silabs.com/documents/public/application-notes/an1374-trustzone.pdf
   */
  NVIC_ClearPendingIRQ(SMU_PRIVILEGED_IRQn);
  SMU->IF_CLR = SMU_IF_PPUPRIV | SMU_IF_PPUINST;
  NVIC_EnableIRQ(SMU_PRIVILEGED_IRQn);
  SMU->IEN = SMU_IEN_PPUPRIV | SMU_IEN_PPUINST;
}
