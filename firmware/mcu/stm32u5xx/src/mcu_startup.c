/*
 * STM32U5 MCU Startup
 *
 * Provides reset handler, interrupt vectors, and system initialization
 * for STM32U585xx microcontroller.
 */

#include "stm32u585xx.h"

#include <stdbool.h>
#include <stdint.h>

/* Linker-defined symbols for memory sections */
extern uint32_t _sidata; /* Start of initialized data in flash */
extern uint32_t _sdata;  /* Start of initialized data in RAM */
extern uint32_t _edata;  /* End of initialized data in RAM */
extern uint32_t _sbss;   /* Start of BSS section */
extern uint32_t _ebss;   /* End of BSS section */
extern uint32_t _estack; /* End of stack (top) */

extern int main(void) __attribute__((noreturn));

/* Clock prescaler tables used by system clock functions */
const uint8_t AHBPrescTable[16] = {0U, 0U, 0U, 0U, 0U, 0U, 0U, 0U, 1U, 2U, 3U, 4U, 6U, 7U, 8U, 9U};
const uint8_t APBPrescTable[8] = {0U, 0U, 0U, 0U, 1U, 2U, 3U, 4U};

/* Function prototypes */
void mcu_default_handler(void);
void mcu_reset_handler(void);
void mcu_system_init(void);

/* Cortex-M Processor Exceptions */
// clang-format off
void NMI_Handler(void) __attribute__((weak, alias("mcu_default_handler")));
void HardFault_Handler(void) __attribute__((weak, alias("mcu_default_handler")));
void MemManage_Handler(void) __attribute__((weak, alias("mcu_default_handler")));
void BusFault_Handler(void) __attribute__((weak, alias("mcu_default_handler")));
void UsageFault_Handler(void) __attribute__((weak, alias("mcu_default_handler")));
void SecureFault_Handler(void) __attribute__((weak, alias("mcu_default_handler")));
void DebugMon_Handler(void) { while(true){} }
void SVC_Handler(void) __attribute__((weak, alias("mcu_default_handler")));
void PendSV_Handler(void) __attribute__((weak, alias("mcu_default_handler")));
void SysTick_Handler(void) __attribute__((weak, alias("mcu_default_handler")));

/* STM32U5xx Specific Interrupts */
void WWDG_IRQHandler(void) { while(true){} }
void PVD_PVM_IRQHandler(void) { while(true){} }
void RTC_IRQHandler(void) { while(true){} }
void RTC_S_IRQHandler(void) { while(true){} }
void TAMP_IRQHandler(void) { while(true){} }
void RAMCFG_IRQHandler(void) { while(true){} }
void FLASH_IRQHandler(void) { while(true){} }
void FLASH_S_IRQHandler(void) { while(true){} }
void GTZC_IRQHandler(void) { while(true){} }
void RCC_IRQHandler(void) { while(true){} }
void RCC_S_IRQHandler(void) { while(true){} }
void EXTI0_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void EXTI1_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void EXTI2_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void EXTI3_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void EXTI4_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void EXTI5_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void EXTI6_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void EXTI7_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void EXTI8_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void EXTI9_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void EXTI10_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void EXTI11_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void EXTI12_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void EXTI13_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void EXTI14_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void EXTI15_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void IWDG_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void GPDMA1_Channel0_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void GPDMA1_Channel1_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void GPDMA1_Channel2_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void GPDMA1_Channel3_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void GPDMA1_Channel4_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void GPDMA1_Channel5_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void GPDMA1_Channel6_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void GPDMA1_Channel7_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void ADC1_IRQHandler(void) { while(true){} }
void DAC1_IRQHandler(void) { while(true){} }
void FDCAN1_IT0_IRQHandler(void) { while(true){} }
void FDCAN1_IT1_IRQHandler(void) { while(true){} }
void TIM1_BRK_IRQHandler(void) { while(true){} }
void TIM1_UP_IRQHandler(void) { while(true){} }
void TIM1_TRG_COM_IRQHandler(void) { while(true){} }
void TIM1_CC_IRQHandler(void) { while(true){} }
void TIM2_IRQHandler(void) { while(true){} }
void TIM3_IRQHandler(void) { while(true){} }
void TIM4_IRQHandler(void) { while(true){} }
void TIM5_IRQHandler(void) { while(true){} }
void TIM6_IRQHandler(void) { while(true){} }
void TIM7_IRQHandler(void) { while(true){} }
void TIM8_BRK_IRQHandler(void) { while(true){} }
void TIM8_UP_IRQHandler(void) { while(true){} }
void TIM8_TRG_COM_IRQHandler(void) { while(true){} }
void TIM8_CC_IRQHandler(void) { while(true){} }
void I2C1_EV_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void I2C1_ER_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void I2C2_EV_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void I2C2_ER_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void SPI1_IRQHandler(void) { while(true){} }
void SPI2_IRQHandler(void) { while(true){} }
void USART1_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void USART2_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void USART3_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void UART4_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void UART5_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void LPUART1_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void LPTIM1_IRQHandler(void) { while(true){} }
void LPTIM2_IRQHandler(void) { while(true){} }
void TIM15_IRQHandler(void) { while(true){} }
void TIM16_IRQHandler(void) { while(true){} }
void TIM17_IRQHandler(void) { while(true){} }
void COMP_IRQHandler(void) { while(true){} }
void OTG_FS_IRQHandler(void) { while(true){} }
void CRS_IRQHandler(void) { while(true){} }
void FMC_IRQHandler(void) { while(true){} }
void OCTOSPI1_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void PWR_S3WU_IRQHandler(void) { while(true){} }
void SDMMC1_IRQHandler(void) { while(true){} }
void SDMMC2_IRQHandler(void) { while(true){} }
void GPDMA1_Channel8_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void GPDMA1_Channel9_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void GPDMA1_Channel10_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void GPDMA1_Channel11_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void GPDMA1_Channel12_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void GPDMA1_Channel13_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void GPDMA1_Channel14_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void GPDMA1_Channel15_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void I2C3_EV_IRQHandler(void) { while(true){} }
void I2C3_ER_IRQHandler(void) { while(true){} }
void SAI1_IRQHandler(void) { while(true){} }
void SAI2_IRQHandler(void) { while(true){} }
void TSC_IRQHandler(void) { while(true){} }
void RNG_IRQHandler(void) { while(true){} }
void FPU_IRQHandler(void) { while(true){} }
void HASH_IRQHandler(void) { while(true){} }
void LPTIM3_IRQHandler(void) { while(true){} }
void SPI3_IRQHandler(void) { while(true){} }
void I2C4_ER_IRQHandler(void) { while(true){} }
void I2C4_EV_IRQHandler(void) { while(true){} }
void MDF1_FLT0_IRQHandler(void) { while(true){} }
void MDF1_FLT1_IRQHandler(void) { while(true){} }
void MDF1_FLT2_IRQHandler(void) { while(true){} }
void MDF1_FLT3_IRQHandler(void) { while(true){} }
void UCPD1_IRQHandler(void) { while(true){} }
void ICACHE_IRQHandler(void) { while(true){} }
void LPTIM4_IRQHandler(void) { while(true){} }
void DCACHE1_IRQHandler(void) { while(true){} }
void ADF1_IRQHandler(void) { while(true){} }
void ADC4_IRQHandler(void) { while(true){} }
void LPDMA1_Channel0_IRQHandler(void) { while(true){} }
void LPDMA1_Channel1_IRQHandler(void) { while(true){} }
void LPDMA1_Channel2_IRQHandler(void) { while(true){} }
void LPDMA1_Channel3_IRQHandler(void) { while(true){} }
void DMA2D_IRQHandler(void) { while(true){} }
void DCMI_PSSI_IRQHandler(void) { while(true){} }
void OCTOSPI2_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void MDF1_FLT4_IRQHandler(void) { while(true){} }
void MDF1_FLT5_IRQHandler(void) { while(true){} }
void CORDIC_IRQHandler(void) { while(true){} }
void FMAC_IRQHandler(void) { while(true){} }
void LSECSSD_IRQHandler(void) { while(true){} }
// clang-format on

/* Exception / Interrupt Vector table */
extern const void* __Vectors[];
const void* __Vectors[] __attribute__((section(".isr_vector"))) = {
  /* Cortex-M Exception Handlers */
  &_estack,            /* Initial Stack Pointer */
  mcu_reset_handler,   /* Reset Handler */
  NMI_Handler,         /* NMI Handler */
  HardFault_Handler,   /* Hard Fault Handler */
  MemManage_Handler,   /* MemManage Handler */
  BusFault_Handler,    /* Bus Fault Handler */
  UsageFault_Handler,  /* Usage Fault Handler */
  SecureFault_Handler, /* Secure Fault Handler */
  0,                   /* Reserved */
  0,                   /* Reserved */
  0,                   /* Reserved */
  SVC_Handler,         /* SVCall Handler */
  DebugMon_Handler,    /* Debug Monitor Handler */
  0,                   /* Reserved */
  PendSV_Handler,      /* PendSV Handler */
  SysTick_Handler,     /* SysTick Handler */

  /* External interrupts */
  WWDG_IRQHandler,             /* WWDG_IRQHandler */
  PVD_PVM_IRQHandler,          /* PVD_PVM_IRQHandler */
  RTC_IRQHandler,              /* RTC_IRQHandler */
  RTC_S_IRQHandler,            /* RTC_S_IRQHandler */
  TAMP_IRQHandler,             /* TAMP_IRQHandler */
  RAMCFG_IRQHandler,           /* RAMCFG_IRQHandler */
  FLASH_IRQHandler,            /* FLASH_IRQHandler */
  FLASH_S_IRQHandler,          /* FLASH_S_IRQHandler */
  GTZC_IRQHandler,             /* GTZC_IRQHandler */
  RCC_IRQHandler,              /* RCC_IRQHandler */
  RCC_S_IRQHandler,            /* RCC_S_IRQHandler */
  EXTI0_IRQHandler,            /* EXTI0_IRQHandler */
  EXTI1_IRQHandler,            /* EXTI1_IRQHandler */
  EXTI2_IRQHandler,            /* EXTI2_IRQHandler */
  EXTI3_IRQHandler,            /* EXTI3_IRQHandler */
  EXTI4_IRQHandler,            /* EXTI4_IRQHandler */
  EXTI5_IRQHandler,            /* EXTI5_IRQHandler */
  EXTI6_IRQHandler,            /* EXTI6_IRQHandler */
  EXTI7_IRQHandler,            /* EXTI7_IRQHandler */
  EXTI8_IRQHandler,            /* EXTI8_IRQHandler */
  EXTI9_IRQHandler,            /* EXTI9_IRQHandler */
  EXTI10_IRQHandler,           /* EXTI10_IRQHandler */
  EXTI11_IRQHandler,           /* EXTI11_IRQHandler */
  EXTI12_IRQHandler,           /* EXTI12_IRQHandler */
  EXTI13_IRQHandler,           /* EXTI13_IRQHandler */
  EXTI14_IRQHandler,           /* EXTI14_IRQHandler */
  EXTI15_IRQHandler,           /* EXTI15_IRQHandler */
  IWDG_IRQHandler,             /* IWDG_IRQHandler */
  0,                           /* Reserved */
  GPDMA1_Channel0_IRQHandler,  /* GPDMA1_Channel0_IRQHandler */
  GPDMA1_Channel1_IRQHandler,  /* GPDMA1_Channel1_IRQHandler */
  GPDMA1_Channel2_IRQHandler,  /* GPDMA1_Channel2_IRQHandler */
  GPDMA1_Channel3_IRQHandler,  /* GPDMA1_Channel3_IRQHandler */
  GPDMA1_Channel4_IRQHandler,  /* GPDMA1_Channel4_IRQHandler */
  GPDMA1_Channel5_IRQHandler,  /* GPDMA1_Channel5_IRQHandler */
  GPDMA1_Channel6_IRQHandler,  /* GPDMA1_Channel6_IRQHandler */
  GPDMA1_Channel7_IRQHandler,  /* GPDMA1_Channel7_IRQHandler */
  ADC1_IRQHandler,             /* ADC1_IRQHandler */
  DAC1_IRQHandler,             /* DAC1_IRQHandler */
  FDCAN1_IT0_IRQHandler,       /* FDCAN1_IT0_IRQHandler */
  FDCAN1_IT1_IRQHandler,       /* FDCAN1_IT1_IRQHandler */
  TIM1_BRK_IRQHandler,         /* TIM1_BRK_IRQHandler */
  TIM1_UP_IRQHandler,          /* TIM1_UP_IRQHandler */
  TIM1_TRG_COM_IRQHandler,     /* TIM1_TRG_COM_IRQHandler */
  TIM1_CC_IRQHandler,          /* TIM1_CC_IRQHandler */
  TIM2_IRQHandler,             /* TIM2_IRQHandler */
  TIM3_IRQHandler,             /* TIM3_IRQHandler */
  TIM4_IRQHandler,             /* TIM4_IRQHandler */
  TIM5_IRQHandler,             /* TIM5_IRQHandler */
  TIM6_IRQHandler,             /* TIM6_IRQHandler */
  TIM7_IRQHandler,             /* TIM7_IRQHandler */
  TIM8_BRK_IRQHandler,         /* TIM8_BRK_IRQHandler */
  TIM8_UP_IRQHandler,          /* TIM8_UP_IRQHandler */
  TIM8_TRG_COM_IRQHandler,     /* TIM8_TRG_COM_IRQHandler */
  TIM8_CC_IRQHandler,          /* TIM8_CC_IRQHandler */
  I2C1_EV_IRQHandler,          /* I2C1_EV_IRQHandler */
  I2C1_ER_IRQHandler,          /* I2C1_ER_IRQHandler */
  I2C2_EV_IRQHandler,          /* I2C2_EV_IRQHandler */
  I2C2_ER_IRQHandler,          /* I2C2_ER_IRQHandler */
  SPI1_IRQHandler,             /* SPI1_IRQHandler */
  SPI2_IRQHandler,             /* SPI2_IRQHandler */
  USART1_IRQHandler,           /* USART1_IRQHandler */
  USART2_IRQHandler,           /* USART2_IRQHandler */
  USART3_IRQHandler,           /* USART3_IRQHandler */
  UART4_IRQHandler,            /* UART4_IRQHandler */
  UART5_IRQHandler,            /* UART5_IRQHandler */
  LPUART1_IRQHandler,          /* LPUART1_IRQHandler */
  LPTIM1_IRQHandler,           /* LPTIM1_IRQHandler */
  LPTIM2_IRQHandler,           /* LPTIM2_IRQHandler */
  TIM15_IRQHandler,            /* TIM15_IRQHandler */
  TIM16_IRQHandler,            /* TIM16_IRQHandler */
  TIM17_IRQHandler,            /* TIM17_IRQHandler */
  COMP_IRQHandler,             /* COMP_IRQHandler */
  OTG_FS_IRQHandler,           /* OTG_FS_IRQHandler */
  CRS_IRQHandler,              /* CRS_IRQHandler */
  FMC_IRQHandler,              /* FMC_IRQHandler */
  OCTOSPI1_IRQHandler,         /* OCTOSPI1_IRQHandler */
  PWR_S3WU_IRQHandler,         /* PWR_S3WU_IRQHandler */
  SDMMC1_IRQHandler,           /* SDMMC1_IRQHandler */
  SDMMC2_IRQHandler,           /* SDMMC2_IRQHandler */
  GPDMA1_Channel8_IRQHandler,  /* GPDMA1_Channel8_IRQHandler */
  GPDMA1_Channel9_IRQHandler,  /* GPDMA1_Channel9_IRQHandler */
  GPDMA1_Channel10_IRQHandler, /* GPDMA1_Channel10_IRQHandler */
  GPDMA1_Channel11_IRQHandler, /* GPDMA1_Channel11_IRQHandler */
  GPDMA1_Channel12_IRQHandler, /* GPDMA1_Channel12_IRQHandler */
  GPDMA1_Channel13_IRQHandler, /* GPDMA1_Channel13_IRQHandler */
  GPDMA1_Channel14_IRQHandler, /* GPDMA1_Channel14_IRQHandler */
  GPDMA1_Channel15_IRQHandler, /* GPDMA1_Channel15_IRQHandler */
  I2C3_EV_IRQHandler,          /* I2C3_EV_IRQHandler */
  I2C3_ER_IRQHandler,          /* I2C3_ER_IRQHandler */
  SAI1_IRQHandler,             /* SAI1_IRQHandler */
  SAI2_IRQHandler,             /* SAI2_IRQHandler */
  TSC_IRQHandler,              /* TSC_IRQHandler */
  0,                           /* Reserved */
  RNG_IRQHandler,              /* RNG_IRQHandler */
  FPU_IRQHandler,              /* FPU_IRQHandler */
  HASH_IRQHandler,             /* HASH_IRQHandler */
  0,                           /* Reserved */
  LPTIM3_IRQHandler,           /* LPTIM3_IRQHandler */
  SPI3_IRQHandler,             /* SPI3_IRQHandler */
  I2C4_ER_IRQHandler,          /* I2C4_ER_IRQHandler */
  I2C4_EV_IRQHandler,          /* I2C4_EV_IRQHandler */
  MDF1_FLT0_IRQHandler,        /* MDF1_FLT0_IRQHandler */
  MDF1_FLT1_IRQHandler,        /* MDF1_FLT1_IRQHandler */
  MDF1_FLT2_IRQHandler,        /* MDF1_FLT2_IRQHandler */
  MDF1_FLT3_IRQHandler,        /* MDF1_FLT3_IRQHandler */
  UCPD1_IRQHandler,            /* UCPD1_IRQHandler */
  ICACHE_IRQHandler,           /* ICACHE_IRQHandler */
  0,                           /* Reserved */
  0,                           /* Reserved */
  LPTIM4_IRQHandler,           /* LPTIM4_IRQHandler */
  DCACHE1_IRQHandler,          /* DCACHE1_IRQHandler */
  ADF1_IRQHandler,             /* ADF1_IRQHandler */
  ADC4_IRQHandler,             /* ADC4_IRQHandler */
  LPDMA1_Channel0_IRQHandler,  /* LPDMA1_Channel0_IRQHandler */
  LPDMA1_Channel1_IRQHandler,  /* LPDMA1_Channel1_IRQHandler */
  LPDMA1_Channel2_IRQHandler,  /* LPDMA1_Channel2_IRQHandler */
  LPDMA1_Channel3_IRQHandler,  /* LPDMA1_Channel3_IRQHandler */
  DMA2D_IRQHandler,            /* DMA2D_IRQHandler */
  DCMI_PSSI_IRQHandler,        /* DCMI_PSSI_IRQHandler */
  OCTOSPI2_IRQHandler,         /* OCTOSPI2_IRQHandler */
  MDF1_FLT4_IRQHandler,        /* MDF1_FLT4_IRQHandler */
  MDF1_FLT5_IRQHandler,        /* MDF1_FLT5_IRQHandler */
  CORDIC_IRQHandler,           /* CORDIC_IRQHandler */
  FMAC_IRQHandler,             /* FMAC_IRQHandler */
  LSECSSD_IRQHandler,          /* LSECSSD_IRQHandler */
};

/* Make sure we have all required linker symbols defined */
/* __StackTop is already declared as extern uint32_t above, so we just need to make sure
 * it points to the right place. The linker should provide _estack. */

/* MSI Range Table - required by STM32U5 LL RCC driver even when using HSE */
const uint32_t MSIRangeTable[16] = {
  48000000U, /* MSI = 48 MHz  */
  24000000U, /* MSI = 24 MHz  */
  16000000U, /* MSI = 16 MHz  */
  12000000U, /* MSI = 12 MHz  */
  4000000U,  /* MSI = 4 MHz   */
  2000000U,  /* MSI = 2 MHz   */
  1333000U,  /* MSI = 1.33 MHz */
  1000000U,  /* MSI = 1 MHz   */
  3072000U,  /* MSI = 3.072 MHz */
  1536000U,  /* MSI = 1.536 MHz */
  1024000U,  /* MSI = 1.024 MHz */
  768000U,   /* MSI = 768 KHz */
  400000U,   /* MSI = 400 KHz */
  200000U,   /* MSI = 200 KHz */
  133000U,   /* MSI = 133 KHz */
  100000U    /* MSI = 100 KHz */
};

void mcu_system_init(void) {
  /* FPU settings ------------------------------------------------------------*/
#if (__FPU_PRESENT == 1) && (__FPU_USED == 1)
  SCB->CPACR |= ((3UL << 20U) | (3UL << 22U)); /* set CP10 and CP11 Full Access */
#endif

  /* Reset the RCC clock configuration to the default reset state ------------*/
  /* Set HSION bit - STM32U5 boots with HSI, keep it on */
  RCC->CR = RCC_CR_HSION;

  /* Reset CFGR registers */
  RCC->CFGR1 = 0U;
  RCC->CFGR2 = 0U;
  RCC->CFGR3 = 0U;

  /* Reset HSEON, CSSON, HSEKERON, PLLxON bits */
  RCC->CR &= ~(RCC_CR_HSEON | RCC_CR_CSSON | RCC_CR_HSIKERON | RCC_CR_HSI48ON | RCC_CR_PLL1ON |
               RCC_CR_PLL2ON | RCC_CR_PLL3ON);

  /* Reset PLLxCFGR register */
  RCC->PLL1CFGR = 0U;
  RCC->PLL2CFGR = 0U;
  RCC->PLL3CFGR = 0U;

  /* Reset PLL1DIVR register */
  RCC->PLL1DIVR = 0x01010280U;
  /* Reset PLL1FRACR register */
  RCC->PLL1FRACR = 0x00000000U;
  /* Reset PLL2DIVR register */
  RCC->PLL2DIVR = 0x01010280U;
  /* Reset PLL2FRACR register */
  RCC->PLL2FRACR = 0x00000000U;
  /* Reset PLL3DIVR register */
  RCC->PLL3DIVR = 0x01010280U;
  /* Reset PLL3FRACR register */
  RCC->PLL3FRACR = 0x00000000U;

  /* Reset HSEBYP bit */
  RCC->CR &= ~(RCC_CR_HSEBYP);

  /* Disable all interrupts */
  RCC->CIER = 0U;

  /* Configure the Vector Table location add offset address ------------------*/
  SCB->VTOR = (uint32_t)&__Vectors;

  /* Check OPSR register to verify if there is an ongoing swap or option bytes update interrupted by
   * a reset - STM32U5 specific */
  uint32_t reg_opsr = FLASH->OPSR & FLASH_OPSR_CODE_OP;
  if ((reg_opsr == FLASH_OPSR_CODE_OP) ||
      (reg_opsr == (FLASH_OPSR_CODE_OP_2 | FLASH_OPSR_CODE_OP_1))) {
    /* Check FLASH Non-secure Control Register access */
    if ((FLASH->NSCR & FLASH_NSCR_OPTLOCK) != 0U) {
      /* Authorizes the Option Byte registers programming */
      FLASH->OPTKEYR = 0x08192A3BU;
      FLASH->OPTKEYR = 0x4C5D6E7FU;
    }
    /* Launch the option bytes change operation */
    FLASH->NSCR |= FLASH_NSCR_OPTSTRT;

    /* Lock the FLASH Option Control Register access */
    FLASH->NSCR |= FLASH_NSCR_OPTLOCK;
  }

  /* Configure default flash wait states - may be adjusted later by clock config */
  FLASH->ACR |= FLASH_ACR_LATENCY_3WS;
}

/* Reset handler - first code executed after reset */
void mcu_reset_handler(void) {
  /* Initialize system (clocks, FPU, etc.) */
  mcu_system_init();

  /* Copy initialized data from flash to RAM */
  uint32_t* src = &_sidata;
  uint32_t* dest = &_sdata;
  while (dest < &_edata) {
    *dest++ = *src++;
  }

  /* Clear BSS section (uninitialized data) */
  dest = &_sbss;
  while (dest < &_ebss) {
    *dest++ = 0;
  }

  /* Initialize C++ static constructors if present */
  extern void __libc_init_array(void) __attribute__((weak));
  if (__libc_init_array) {
    __libc_init_array();
  }

  /* Jump to main application */
  main();
}

/* Default handler for exceptions/interrupts */
void mcu_default_handler(void) {
  /* Disable interrupts to prevent cascading faults */
  __disable_irq();

  /* Infinite loop - debugger can break here */
  for (;;) {
    __NOP();
  }
}
