#include "application_properties.h"
#include "mcu_reset.h"

#include "em_device.h"

#include <stdbool.h>
#include <stdint.h>

/* Linker symbols */
extern uint32_t __etext;
extern uint32_t __data_start__;
extern uint32_t __data_end__;
extern uint32_t __copy_table_start__;
extern uint32_t __copy_table_end__;
extern uint32_t __zero_table_start__;
extern uint32_t __zero_table_end__;
extern uint32_t __bss_start__;
extern uint32_t __bss_end__;
extern uint32_t __StackTop;

extern int main(void) __attribute__((noreturn));

void mcu_default_handler(void);
void mcu_reset_handler(void);

static void mcu_system_init(void);

/* Cortex-M Processor Exceptions */
// clang-format off
void NMI_Handler(void) __attribute__((weak, alias("mcu_default_handler")));
void HardFault_Handler(void) __attribute__((weak, alias("mcu_default_handler")));
void MemManage_Handler(void) __attribute__((weak, alias("mcu_default_handler")));
void BusFault_Handler(void) __attribute__((weak, alias("mcu_default_handler")));
void UsageFault_Handler(void) __attribute__((weak, alias("mcu_default_handler")));
void DebugMon_Handler(void) { while(true){} }
void SVC_Handler(void) __attribute__((weak, alias("mcu_default_handler")));
void PendSV_Handler(void) __attribute__((weak, alias("mcu_default_handler")));
void SysTick_Handler(void) __attribute__((weak, alias("mcu_default_handler")));

/* Provide a dummy value for the sl_app_properties symbol. */
void sl_app_properties(void) __attribute__((weak));

/* Part Specific Interrupts */
void SMU_SECURE_IRQHandler(void) { while(true){} }
void SMU_PRIVILEGED_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void SMU_NS_PRIVILEGED_IRQHandler(void) { while(true){} }
void EMU_IRQHandler(void) { while(true){} }
void TIMER0_IRQHandler(void) { while(true){} }
void TIMER1_IRQHandler(void) { while(true){} }
void TIMER2_IRQHandler(void) { while(true){} }
void TIMER3_IRQHandler(void) { while(true){} }
void TIMER4_IRQHandler(void) { while(true){} }
void USART0_RX_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void USART0_TX_IRQHandler(void) { while(true){} }
void EUSART0_RX_IRQHandler(void) { while(true){} }
void EUSART0_TX_IRQHandler(void) { while(true){} }
void EUSART1_RX_IRQHandler(void) { while(true){} }
void EUSART1_TX_IRQHandler(void) { while(true){} }
void MVP_IRQHandler(void) { while(true){} }
void ICACHE0_IRQHandler(void) { while(true){} }
void BURTC_IRQHandler(void) { while(true){} }
void LETIMER0_IRQHandler(void) { while(true){} }
void SYSCFG_IRQHandler(void) { while(true){} }
void MPAHBRAM_IRQHandler(void) { while(true){} }
void LDMA_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void LFXO_IRQHandler(void) { while(true){} }
void LFRCO_IRQHandler(void) { while(true){} }
void ULFRCO_IRQHandler(void) { while(true){} }
void GPIO_ODD_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void GPIO_EVEN_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void I2C0_IRQHandler(void) { while(true){} }
void I2C1_IRQHandler(void) { while(true){} }
void EMUDG_IRQHandler(void) { while(true){} }
void AGC_IRQHandler(void) { while(true){} }
void BUFC_IRQHandler(void) { while(true){} }
void FRC_PRI_IRQHandler(void) { while(true){} }
void FRC_IRQHandler(void) { while(true){} }
void MODEM_IRQHandler(void) { while(true){} }
void PROTIMER_IRQHandler(void) { while(true){} }
void RAC_RSM_IRQHandler(void) { while(true){} }
void RAC_SEQ_IRQHandler(void) { while(true){} }
void HOSTMAILBOX_IRQHandler(void) { while(true){} }
void SYNTH_IRQHandler(void) { while(true){} }
void ACMP0_IRQHandler(void) { while(true){} }
void ACMP1_IRQHandler(void) { while(true){} }
void WDOG0_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void WDOG1_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void HFXO0_IRQHandler(void) { while(true){} }
void HFRCO0_IRQHandler(void) { while(true){} }
void HFRCOEM23_IRQHandler(void) { while(true){} }
void CMU_IRQHandler(void) { while(true){} }
void AES_IRQHandler(void) { while(true){} }
void IADC_IRQHandler(void) { while(true){} }
void MSC_IRQHandler(void) { while(true){} }
void DPLL0_IRQHandler(void) { while(true){} }
void EMUEFP_IRQHandler(void) { while(true){} }
void DCDC_IRQHandler(void) { while(true){} }
void PCNT0_IRQHandler(void) { while(true){} }
void SW0_IRQHandler(void) { while(true){} }
void SW1_IRQHandler(void) { while(true){} }
void SW2_IRQHandler(void) { while(true){} }
void SW3_IRQHandler(void) { while(true){} }
void KERNEL0_IRQHandler(void) { while(true){} }
void KERNEL1_IRQHandler(void) { while(true){} }
void M33CTI0_IRQHandler(void) { while(true){} }
void M33CTI1_IRQHandler(void) { while(true){} }
void FPUEXH_IRQHandler(void) { while(true){} }
void SETAMPERHOST_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void SEMBRX_IRQHandler(void) __attribute__((weak, alias("mcu_default_handler")));
void SEMBTX_IRQHandler(void) { while(true){} }
void SYSRTC_APP_IRQHandler(void) { while(true){} }
void SYSRTC_SEQ_IRQHandler(void) { while(true){} }
void KEYSCAN_IRQHandler(void) { while(true){} }
void RFECA0_IRQHandler(void) { while(true){} }
void RFECA1_IRQHandler(void) { while(true){} }
void VDAC0_IRQHandler(void) { while(true){} }
void VDAC1_IRQHandler(void) { while(true){} }
void AHB2AHB0_IRQHandler(void) { while(true){} }
void AHB2AHB1_IRQHandler(void) { while(true){} }
// clang-format on

/*----------------------------------------------------------------------------
 * Exception / Interrupt Vector table
 *----------------------------------------------------------------------------*/

// For the application, we have "two" vector tables. .vectors is at the base of
// the firmware image, but is (accidentally) not aligned to the correct alignment
// of 512 bytes.
// The bootloader jumps here, and then we set VTOR appropriately to the real vector
// table below, which is correctly aligned via the linkerscript.
// The bootloader just one vector table.
#ifdef IMAGE_TYPE_APPLICATION
extern const tVectorEntry __Vectors[];
const tVectorEntry __Vectors[] __attribute__((section(".vectors"))) = {
  /* Cortex-M Exception Handlers */
  {.topOfStack = &__StackTop}, /*      Initial Stack Pointer     */
  {mcu_reset_handler},         /*      Reset Handler             */
};
#endif

#ifdef IMAGE_TYPE_APPLICATION
extern const tVectorEntry __VectorsReal[];
const tVectorEntry __VectorsReal[] __attribute__((section(".vectors_real"))) = {
#else
extern const tVectorEntry __Vectors[];
const tVectorEntry __Vectors[] __attribute__((section(".vectors"))) = {
#endif
  /* Cortex-M Exception Handlers */
  {.topOfStack = &__StackTop}, /*      Initial Stack Pointer     */
  {mcu_reset_handler},         /*      Reset Handler             */
  {NMI_Handler},               /*      NMI Handler               */
  {HardFault_Handler},         /*      Hard Fault Handler        */
  {MemManage_Handler},         /*      MPU Fault Handler         */
  {BusFault_Handler},          /*      Bus Fault Handler         */
  {UsageFault_Handler},        /*      Usage Fault Handler       */
  {mcu_default_handler},       /*      Reserved                  */
  {mcu_default_handler},       /*      Reserved                  */
  {mcu_default_handler},       /*      Reserved                  */
  {mcu_default_handler},       /*      Reserved                  */
  {SVC_Handler},               /*      SVCall Handler            */
  {DebugMon_Handler},          /*      Debug Monitor Handler     */
  {sl_app_properties},         /*      Application properties    */
  {PendSV_Handler},            /*      PendSV Handler            */
  {SysTick_Handler},           /*      SysTick Handler           */

  /* External interrupts */
  {SMU_SECURE_IRQHandler},        /* -16 = SMU_SECURE */
  {SMU_PRIVILEGED_IRQHandler},    /* -15 = SMU_PRIVILEGED */
  {SMU_NS_PRIVILEGED_IRQHandler}, /* -14 = SMU_NS_PRIVILEGED */
  {EMU_IRQHandler},               /* -13 = EMU */
  {TIMER0_IRQHandler},            /* -12 = TIMER0 */
  {TIMER1_IRQHandler},            /* -11 = TIMER1 */
  {TIMER2_IRQHandler},            /* -10 = TIMER2 */
  {TIMER3_IRQHandler},            /* -9 = TIMER3 */
  {TIMER4_IRQHandler},            /* -8 = TIMER4 */
  {USART0_RX_IRQHandler},         /* -7 = USART0_RX */
  {USART0_TX_IRQHandler},         /* -6 = USART0_TX */
  {EUSART0_RX_IRQHandler},        /* -5 = EUSART0_RX */
  {EUSART0_TX_IRQHandler},        /* -4 = EUSART0_TX */
  {EUSART1_RX_IRQHandler},        /* -3 = EUSART1_RX */
  {EUSART1_TX_IRQHandler},        /* -2 = EUSART1_TX */
  {MVP_IRQHandler},               /* -1 = MVP */
  {ICACHE0_IRQHandler},           /* 00 = ICACHE0 */
  {BURTC_IRQHandler},             /* 01 = BURTC */
  {LETIMER0_IRQHandler},          /* 02 = LETIMER0 */
  {SYSCFG_IRQHandler},            /* 03 = SYSCFG */
  {MPAHBRAM_IRQHandler},          /* 04 = MPAHBRAM */
  {LDMA_IRQHandler},              /* 05 = LDMA */
  {LFXO_IRQHandler},              /* 06 = LFXO */
  {LFRCO_IRQHandler},             /* 07 = LFRCO */
  {ULFRCO_IRQHandler},            /* 08 = ULFRCO */
  {GPIO_ODD_IRQHandler},          /* 09 = GPIO_ODD */
  {GPIO_EVEN_IRQHandler},         /* 10 = GPIO_EVEN */
  {I2C0_IRQHandler},              /* 11 = I2C0 */
  {I2C1_IRQHandler},              /* 12 = I2C1 */
  {EMUDG_IRQHandler},             /* 13 = EMUDG */
  {AGC_IRQHandler},               /* 14 = AGC */
  {BUFC_IRQHandler},              /* 15 = BUFC */
  {FRC_PRI_IRQHandler},           /* 16 = FRC_PRI */
  {FRC_IRQHandler},               /* 17 = FRC */
  {MODEM_IRQHandler},             /* 18 = MODEM */
  {PROTIMER_IRQHandler},          /* 19 = PROTIMER */
  {RAC_RSM_IRQHandler},           /* 20 = RAC_RSM */
  {RAC_SEQ_IRQHandler},           /* 21 = RAC_SEQ */
  {HOSTMAILBOX_IRQHandler},       /* 22 = HOSTMAILBOX */
  {SYNTH_IRQHandler},             /* 23 = SYNTH */
  {ACMP0_IRQHandler},             /* 24 = ACMP0 */
  {ACMP1_IRQHandler},             /* 25 = ACMP1 */
  {WDOG0_IRQHandler},             /* 26 = WDOG0 */
  {WDOG1_IRQHandler},             /* 27 = WDOG1 */
  {HFXO0_IRQHandler},             /* 28 = HFXO0 */
  {HFRCO0_IRQHandler},            /* 29 = HFRCO0 */
  {HFRCOEM23_IRQHandler},         /* 30 = HFRCOEM23 */
  {CMU_IRQHandler},               /* 31 = CMU */
  {AES_IRQHandler},               /* 32 = AES */
  {IADC_IRQHandler},              /* 33 = IADC */
  {MSC_IRQHandler},               /* 34 = MSC */
  {DPLL0_IRQHandler},             /* 35 = DPLL0 */
  {EMUEFP_IRQHandler},            /* 36 = EMUEFP */
  {DCDC_IRQHandler},              /* 37 = DCDC */
  {PCNT0_IRQHandler},             /* 38 = PCNT0 */
  {SW0_IRQHandler},               /* 39 = SW0 */
  {SW1_IRQHandler},               /* 40 = SW1 */
  {SW2_IRQHandler},               /* 41 = SW2 */
  {SW3_IRQHandler},               /* 42 = SW3 */
  {KERNEL0_IRQHandler},           /* 43 = KERNEL0 */
  {KERNEL1_IRQHandler},           /* 44 = KERNEL1 */
  {M33CTI0_IRQHandler},           /* 45 = M33CTI0 */
  {M33CTI1_IRQHandler},           /* 46 = M33CTI1 */
  {FPUEXH_IRQHandler},            /* 47 = FPUEXH */
  {SETAMPERHOST_IRQHandler},      /* 48 = SETAMPERHOST */
  {SEMBRX_IRQHandler},            /* 49 = SEMBRX */
  {SEMBTX_IRQHandler},            /* 50 = SEMBTX */
  {SYSRTC_APP_IRQHandler},        /* 51 = SYSRTC_APP */
  {SYSRTC_SEQ_IRQHandler},        /* 52 = SYSRTC_SEQ */
  {KEYSCAN_IRQHandler},           /* 53 = KEYSCAN */
  {RFECA0_IRQHandler},            /* 54 = RFECA0 */
  {RFECA1_IRQHandler},            /* 55 = RFECA1 */
  {VDAC0_IRQHandler},             /* 56 = VDAC0 */
  {VDAC1_IRQHandler},             /* 57 = VDAC1 */
  {AHB2AHB0_IRQHandler},          /* 58 = AHB2AHB0 */
  {AHB2AHB1_IRQHandler},          /* 59 = AHB2AHB1 */
};

// Note: globals cannot be used in this function
void mcu_system_init(void) {
  volatile mcu_reset_reason_t reason = mcu_reset_get_reason();
  (void)reason;

#if defined(__VTOR_PRESENT) && (__VTOR_PRESENT == 1U)
#ifdef IMAGE_TYPE_APPLICATION
  SCB->VTOR = (uint32_t)&__VectorsReal;
#else
  SCB->VTOR = (uint32_t)&__Vectors;
#endif
#endif

#if defined(UNALIGNED_SUPPORT_DISABLE)
  SCB->CCR |= SCB_CCR_UNALIGN_TRP_Msk;
#endif

#if (__FPU_PRESENT == 1)
  SCB->CPACR |= ((3U << 10U * 2U)     /* set CP10 Full Access */
                 | (3U << 11U * 2U)); /* set CP11 Full Access */
#endif
}

/*----------------------------------------------------------------------------
 * Reset Handler called on controller reset
 *----------------------------------------------------------------------------*/
void mcu_reset_handler(void) {
  uint32_t *pSrc, *pDest;
  uint32_t* pTable __attribute__((unused));

  mcu_system_init();

  /*  Firstly it copies data from read only memory to RAM. There are two schemes
   *  to copy. One can copy more than one sections. Another can only copy
   *  one section.  The former scheme needs more instructions and read-only
   *  data to implement than the latter.
   *  Macro __STARTUP_COPY_MULTIPLE is used to choose between two schemes.  */

  /*  Single section scheme.
   *
   *  The ranges of copy from/to are specified by following symbols
   *    __etext: LMA of start of the section to copy from. Usually end of text
   *    __data_start__: VMA of start of the section to copy to
   *    __data_end__: VMA of end of the section to copy to
   *
   *  All addresses must be aligned to 4 bytes boundary.
   */
  pSrc = &__etext;
  pDest = &__data_start__;

  for (; pDest < &__data_end__;) {
    *pDest++ = *pSrc++;
  }

  /*  Single BSS section scheme.
   *
   *  The BSS section is specified by following symbols
   *    __bss_start__: start of the BSS section.
   *    __bss_end__: end of the BSS section.
   *
   *  Both addresses must be aligned to 4 bytes boundary.
   */
  pDest = &__bss_start__;

  for (; pDest < &__bss_end__;) {
    *pDest++ = 0UL;
  }

  main();
}

/*----------------------------------------------------------------------------
 * Default Handler for Exceptions / Interrupts
 *----------------------------------------------------------------------------*/
void mcu_default_handler(void) {
  mcu_reset_with_reason(MCU_RESET_FAULT);
}
