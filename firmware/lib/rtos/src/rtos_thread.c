#include "assert.h"
#include "bitops.h"
#include "mcu.h"
#include "mcu_nvic.h"
#include "mcu_systick.h"
#include "rtos.h"
#include "task.h"

#include <string.h>

extern void xPortSysTickHandler(void);
extern void vApplicationGetIdleTaskMemory(StaticTask_t** ppxIdleTaskTCBBuffer,
                                          StackType_t** ppxIdleTaskStackBuffer,
                                          uint32_t* pulIdleTaskStackSize);
extern void vApplicationGetTimerTaskMemory(StaticTask_t** ppxTimerTaskTCBBuffer,
                                           StackType_t** ppxTimerTaskStackBuffer,
                                           uint32_t* pulTimerTaskStackSize);
void rtos_thread_create_static(rtos_thread_t* thread, void (*func)(void*), const char* name,
                               void* args, rtos_thread_priority_t priority, uint32_t* stack_buffer,
                               uint32_t stack_size, StaticTask_t* task_buffer,
                               rtos_thread_mpu_t mpu_regions) {
  ASSERT(IS_POWER_OF_TWO(stack_size));

#if (configENABLE_MPU == 1)
  ASSERT(mpu_regions.privilege == rtos_thread_unprivileged_bit ||
         mpu_regions.privilege == rtos_thread_privileged_bit);

  TaskParameters_t pxTaskDefinition = {.pvTaskCode = func,
                                       .pcName = name,
                                       .usStackDepth = ((uint16_t)stack_size / sizeof(uint32_t)),
                                       .pvParameters = args,
                                       .uxPriority = (priority | mpu_regions.privilege),
                                       .puxStackBuffer = stack_buffer,
                                       .xRegions = {},
                                       .pxTaskBuffer = (StaticTask_t*)task_buffer};
  memcpy(pxTaskDefinition.xRegions, mpu_regions.regions, sizeof(mpu_regions.regions));
  xTaskCreateRestrictedStatic(&pxTaskDefinition, (TaskHandle_t*)&(thread->handle));
#else
  // When MPU is disabled, use the standard static task creation
  // MPU regions parameter is ignored when MPU is not enabled
  (void)mpu_regions;

  TaskHandle_t task_handle =
    xTaskCreateStatic(func,                           // Task function
                      name,                           // Task name
                      stack_size / sizeof(uint32_t),  // Stack size in words
                      args,                           // Task parameters
                      priority,                       // Priority
                      (StackType_t*)stack_buffer,     // Stack buffer
                      task_buffer                     // Task control block buffer
    );

  thread->handle = (uintptr_t)task_handle;
  ASSERT(task_handle != NULL);
#endif
}

void rtos_thread_delete(rtos_thread_t* thread) {
  if (thread == NULL) {
    vTaskDelete(NULL);
  } else {
    vTaskDelete((TaskHandle_t)thread->handle);
  }
}

/* configSUPPORT_STATIC_ALLOCATION is set to 1, so the application must provide an
implementation of vApplicationGetIdleTaskMemory() to provide the memory that is
used by the Idle task. */
void vApplicationGetIdleTaskMemory(StaticTask_t** ppxIdleTaskTCBBuffer,
                                   StackType_t** ppxIdleTaskStackBuffer,
                                   uint32_t* pulIdleTaskStackSize) {
  /* If the buffers to be provided to the Idle task are declared inside this
  function then they must be declared static - otherwise they will be allocated on
  the stack and so not exists after this function exits. */
  static StaticTask_t xIdleTaskTCB;
  static StackType_t uxIdleTaskStack[configMINIMAL_STACK_SIZE];

  /* Pass out a pointer to the StaticTask_t structure in which the Idle task's
  state will be stored. */
  *ppxIdleTaskTCBBuffer = &xIdleTaskTCB;

  /* Pass out the array that will be used as the Idle task's stack. */
  *ppxIdleTaskStackBuffer = uxIdleTaskStack;

  /* Pass out the size of the array pointed to by *ppxIdleTaskStackBuffer.
  Note that, as the array is necessarily of type StackType_t,
  configMINIMAL_STACK_SIZE is specified in words, not bytes. */
  *pulIdleTaskStackSize = configMINIMAL_STACK_SIZE;
}

/* configSUPPORT_STATIC_ALLOCATION and configUSE_TIMERS are both set to 1, so the
application must provide an implementation of vApplicationGetTimerTaskMemory()
to provide the memory that is used by the Timer service task. */
void vApplicationGetTimerTaskMemory(StaticTask_t** ppxTimerTaskTCBBuffer,
                                    StackType_t** ppxTimerTaskStackBuffer,
                                    uint32_t* pulTimerTaskStackSize) {
  /* If the buffers to be provided to the Timer task are declared inside this
  function then they must be declared static - otherwise they will be allocated on
  the stack and so not exists after this function exits. */
  static StaticTask_t xTimerTaskTCB;
  static StackType_t uxTimerTaskStack[configTIMER_TASK_STACK_DEPTH];

  /* Pass out a pointer to the StaticTask_t structure in which the Timer
  task's state will be stored. */
  *ppxTimerTaskTCBBuffer = &xTimerTaskTCB;

  /* Pass out the array that will be used as the Timer task's stack. */
  *ppxTimerTaskStackBuffer = uxTimerTaskStack;

  /* Pass out the size of the array pointed to by *ppxTimerTaskStackBuffer.
  Note that, as the array is necessarily of type StackType_t,
  configTIMER_TASK_STACK_DEPTH is specified in words, not bytes. */
  *pulTimerTaskStackSize = configTIMER_TASK_STACK_DEPTH;
}

void rtos_thread_start_scheduler(void) {
#if (__ARM_ARCH_7A__ == 0U)
  /* Service Call interrupt might be configured before kernel start     */
  /* and when its priority is lower or equal to BASEPRI, svc instruction */
  /* causes a Hard Fault.                                               */
  mcu_nvic_set_priority(SVCall_IRQn, 0U);
#endif

  vTaskStartScheduler();
}

void rtos_thread_sleep(const uint32_t time_ms) {
  if (time_ms == RTOS_THREAD_TIMEOUT_MAX)
    vTaskDelay(portMAX_DELAY);
  else
    vTaskDelay((portTickType)MS2TICKS(time_ms));
}

/* rtos_thread_systime should only be used with hal_thread_* / rtos calls as it _will_ wrap at
 * UINT32_MAX, which is handled by freertos */
uint32_t rtos_thread_systime(void) {
  return (uint32_t)TICKS2MS(xTaskGetTickCount());
}

uint64_t rtos_thread_micros(void) {
  volatile uint64_t micros = 0;
  micros = 1000 * rtos_thread_systime();
  micros +=
    ((mcu_systick_get_reload() - mcu_systick_get_value()) / (mcu_systick_get_reload() / 1000));
  return micros;
}

bool rtos_in_isr(void) {
  return xPortIsInsideInterrupt() == pdTRUE;
}
