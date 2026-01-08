#pragma once

/* MCU specific FreeRTOS support */

#ifdef EMBEDDED_BUILD
/* Config assert printing */
#include "printf.h"
#define configASSERT(x)                                                               \
  if ((x) == 0) {                                                                     \
    printf("configASSERT at %s:%d in function %s\r\n", __FILE__, __LINE__, __func__); \
    taskDISABLE_INTERRUPTS();                                                         \
    for (;;)                                                                          \
      ;                                                                               \
  }

/* Run-time statistics */
#include "mcu_debug.h"
#define portCONFIGURE_TIMER_FOR_RUN_TIME_STATS() mcu_debug_dwt_enable()
#define portGET_RUN_TIME_COUNTER_VALUE()         mcu_debug_dwt_cycle_counter()
#endif
