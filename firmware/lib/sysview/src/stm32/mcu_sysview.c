#if defined(USE_SYSVIEW) && (USE_SYSVIEW == 1) && defined(IMAGE_TYPE_APPLICATION) && \
  (IMAGE_TYPE_APPLICATION == 1)

#include "SEGGER_SYSTEMVIEW_Description.h"
#include "SEGGER_SYSVIEW.h"
#include "attributes.h"
#include "stm32u585xx.h"

#include <stdbool.h>
#include <stdio.h>

extern void SEGGER_SYSVIEW_SendSysDesc(const char* sSysDesc);

static unsigned int SHARED_TASK_BSS s_peripherals_event_offset;
static bool SHARED_TASK_BSS s_modules_registered;
static void SYSVIEW_SendPeripheralsDescription(void);
static SEGGER_SYSVIEW_MODULE SHARED_TASK_BSS s_sysview_peripherals_module;

static void send_irq_desc(IRQn_Type irqn, const char* name) {
  char desc[48];
  (void)snprintf(desc, sizeof(desc), "I#%d=%s", 16 + (int)irqn, name);
  SEGGER_SYSVIEW_SendSysDesc(desc);
}

static void SYSVIEW_SendPeripheralsDescription(void) {
  SEGGER_SYSVIEW_RecordModuleDescription(&s_sysview_peripherals_module, "T=Peripherals");
}

void SYSVIEW_RegisterModules(void) {
  if (s_modules_registered) {
    return;
  }

  s_sysview_peripherals_module.sModule = "M=Peripherals";
  s_sysview_peripherals_module.NumEvents = (SYSVIEW_PERIPHERALS_API_ID_MAX + 1u);
  s_sysview_peripherals_module.EventOffset = 0u;
  s_sysview_peripherals_module.pfSendModuleDesc = SYSVIEW_SendPeripheralsDescription;
  s_sysview_peripherals_module.pNext = 0;

  SEGGER_SYSVIEW_RegisterModule(&s_sysview_peripherals_module);
  s_peripherals_event_offset = s_sysview_peripherals_module.EventOffset;
  s_modules_registered = true;
}

unsigned int SYSVIEW_GetPeripheralsEventOffset(void) {
  return s_peripherals_event_offset;
}

void SYSVIEW_SendInterruptList(void) {
  send_irq_desc(EXTI0_IRQn, "EXTI0");
  send_irq_desc(EXTI1_IRQn, "EXTI1");
  send_irq_desc(EXTI2_IRQn, "EXTI2");
  send_irq_desc(EXTI3_IRQn, "EXTI3");
  send_irq_desc(EXTI4_IRQn, "EXTI4");
  send_irq_desc(EXTI5_IRQn, "EXTI5");
  send_irq_desc(EXTI6_IRQn, "EXTI6");
  send_irq_desc(EXTI7_IRQn, "EXTI7");
  send_irq_desc(EXTI8_IRQn, "EXTI8");
  send_irq_desc(EXTI9_IRQn, "EXTI9");
  send_irq_desc(EXTI10_IRQn, "EXTI10");
  send_irq_desc(EXTI11_IRQn, "EXTI11");
  send_irq_desc(EXTI12_IRQn, "EXTI12");
  send_irq_desc(EXTI13_IRQn, "EXTI13");
  send_irq_desc(EXTI14_IRQn, "EXTI14");
  send_irq_desc(EXTI15_IRQn, "EXTI15");
  send_irq_desc(IWDG_IRQn, "IWDG");
  send_irq_desc(GPDMA1_Channel0_IRQn, "GPDMA1_Channel0");
  send_irq_desc(GPDMA1_Channel1_IRQn, "GPDMA1_Channel1");
  send_irq_desc(GPDMA1_Channel2_IRQn, "GPDMA1_Channel2");
  send_irq_desc(GPDMA1_Channel3_IRQn, "GPDMA1_Channel3");
  send_irq_desc(GPDMA1_Channel4_IRQn, "GPDMA1_Channel4");
  send_irq_desc(GPDMA1_Channel5_IRQn, "GPDMA1_Channel5");
  send_irq_desc(GPDMA1_Channel6_IRQn, "GPDMA1_Channel6");
  send_irq_desc(GPDMA1_Channel7_IRQn, "GPDMA1_Channel7");
  send_irq_desc(GPDMA1_Channel8_IRQn, "GPDMA1_Channel8");
  send_irq_desc(GPDMA1_Channel9_IRQn, "GPDMA1_Channel9");
  send_irq_desc(GPDMA1_Channel10_IRQn, "GPDMA1_Channel10");
  send_irq_desc(GPDMA1_Channel11_IRQn, "GPDMA1_Channel11");
  send_irq_desc(GPDMA1_Channel12_IRQn, "GPDMA1_Channel12");
  send_irq_desc(GPDMA1_Channel13_IRQn, "GPDMA1_Channel13");
  send_irq_desc(GPDMA1_Channel14_IRQn, "GPDMA1_Channel14");
  send_irq_desc(GPDMA1_Channel15_IRQn, "GPDMA1_Channel15");
  send_irq_desc(I2C1_EV_IRQn, "I2C1_EV");
  send_irq_desc(I2C1_ER_IRQn, "I2C1_ER");
  send_irq_desc(I2C2_EV_IRQn, "I2C2_EV");
  send_irq_desc(I2C2_ER_IRQn, "I2C2_ER");
  send_irq_desc(USART1_IRQn, "USART1");
  send_irq_desc(USART2_IRQn, "USART2");
  send_irq_desc(USART3_IRQn, "USART3");
  send_irq_desc(UART4_IRQn, "UART4");
  send_irq_desc(UART5_IRQn, "UART5");
  send_irq_desc(LPUART1_IRQn, "LPUART1");
  send_irq_desc(OCTOSPI1_IRQn, "OCTOSPI1");
  send_irq_desc(OCTOSPI2_IRQn, "OCTOSPI2");
}

#endif
