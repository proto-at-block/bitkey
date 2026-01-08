#pragma once

#ifdef EMBEDDED_BUILD
#include "mcu_freertos.h"
#include "memfault/ports/freertos_trace.h"
#endif

/* Defines needed by FreeRTOS to implement CMSIS RTOS2 API. Do not change! */
#define configCPU_CLOCK_HZ                      (80000000)
#define configSUPPORT_STATIC_ALLOCATION         1
#define configSUPPORT_DYNAMIC_ALLOCATION        1
#define configUSE_PREEMPTION                    1
#define configUSE_TIMERS                        1
#define configUSE_MUTEXES                       1
#define configUSE_RECURSIVE_MUTEXES             1
#define configUSE_COUNTING_SEMAPHORES           1
#define configUSE_TASK_NOTIFICATIONS            1
#define configUSE_TRACE_FACILITY                1
#define configUSE_16_BIT_TICKS                  0
#define configUSE_PORT_OPTIMISED_TASK_SELECTION 0
#define configMAX_PRIORITIES                    5
#define configKERNEL_INTERRUPT_PRIORITY         255
#define configRECORD_STACK_HIGH_ADDRESS         0

#define configMINIMAL_STACK_SIZE             160
#define configTOTAL_HEAP_SIZE                54000
#define configAPPLICATION_ALLOCATED_HEAP     1
#define configTICK_RATE_HZ                   1000
#define configTIMER_TASK_STACK_DEPTH         250
#define configTIMER_TASK_PRIORITY            (configMAX_PRIORITIES - 1)  // max priority
#define configTIMER_QUEUE_LENGTH             13
#define configMAX_SYSCALL_INTERRUPT_PRIORITY 48
#define configUSE_TIME_SLICING               1
#define configIDLE_SHOULD_YIELD              1

#define configCHECK_FOR_STACK_OVERFLOW 0
// TODO: Set this to 2 and defined stack-overflow checking function.
// #define configCHECK_FOR_STACK_OVERFLOW               2
#define configUSE_IDLE_HOOK                          0
#define configUSE_TICK_HOOK                          0
#define configUSE_DAEMON_TASK_STARTUP_HOOK           0
#define configUSE_MALLOC_FAILED_HOOK                 0
#define configQUEUE_REGISTRY_SIZE                    10
#define configENABLE_FPU                             1
#define configENABLE_MPU                             1
#define configMINIMAL_SECURE_STACK_SIZE              128
#define configNUM_USER_THREAD_LOCAL_STORAGE_POINTERS 0
#define configUSE_POSIX_ERRNO                        0

/* Defines that include FreeRTOS functions which implement CMSIS RTOS2 API. Do not change! */
#define INCLUDE_xEventGroupSetBitsFromISR   1
#define INCLUDE_xSemaphoreGetMutexHolder    1
#define INCLUDE_vTaskDelay                  1
#define INCLUDE_vTaskDelayUntil             1
#define INCLUDE_vTaskDelete                 1
#define INCLUDE_xTaskGetCurrentTaskHandle   1
#define INCLUDE_xTaskGetSchedulerState      1
#define INCLUDE_uxTaskGetStackHighWaterMark 1
#define INCLUDE_uxTaskPriorityGet           1
#define INCLUDE_vTaskPrioritySet            1
#define INCLUDE_eTaskGetState               1
#define INCLUDE_vTaskSuspend                1
#define INCLUDE_xTimerPendFunctionCall      1
#define INCLUDE_uxTaskGetRunTime            1

/* Energy saving modes. */
#if defined(SL_CATALOG_POWER_MANAGER_PRESENT)
#define configUSE_TICKLESS_IDLE 1
#else
#define configUSE_TICKLESS_IDLE 0
#endif

/* Definition used by Keil to replace default system clock source. */
#define configOVERRIDE_DEFAULT_TICK_CONFIGURATION 0

/* Maximum size of task name. */
#define configMAX_TASK_NAME_LEN 20

/* Use queue sets? */
#define configUSE_QUEUE_SETS 0

/* Generate run-time statistics? */
#define configGENERATE_RUN_TIME_STATS 1

/* Co-routine related definitions. */
#define configUSE_CO_ROUTINES           0
#define configMAX_CO_ROUTINE_PRIORITIES 1

/* Optional resume from ISR functionality. */
#define INCLUDE_xResumeFromISR 1

/* FreeRTOS Secure Side Only and TrustZone Security Extension */
#define configRUN_FREERTOS_SECURE_ONLY 1
#define configENABLE_TRUSTZONE         0

/* Thread local storage pointers used by the SDK */
#ifndef configNUM_SDK_THREAD_LOCAL_STORAGE_POINTERS
#define configNUM_SDK_THREAD_LOCAL_STORAGE_POINTERS 0
#endif

/* PRINT_STRING implementation. iostream_retarget_stdio or third party
   printf should be added if this is used */
#define configPRINT_STRING(X) printf(X)

#define configNUM_THREAD_LOCAL_STORAGE_POINTERS \
  (configNUM_USER_THREAD_LOCAL_STORAGE_POINTERS + configNUM_SDK_THREAD_LOCAL_STORAGE_POINTERS)

// MPU config vars
// https://www.freertos.org/a00110.html#configPROTECTED_KERNEL_OBJECT_POOL_SIZE
#define configPROTECTED_KERNEL_OBJECT_POOL_SIZE                256
#define configSYSTEM_CALL_STACK_SIZE                           128
#define configINCLUDE_APPLICATION_DEFINED_PRIVILEGED_FUNCTIONS 0
#define configTOTAL_MPU_REGIONS                                16
#define configENFORCE_SYSTEM_CALLS_FROM_KERNEL_ONLY            0
#define configALLOW_UNPRIVILEGED_CRITICAL_SECTIONS             1
#define configUSE_MPU_WRAPPERS_V1                              1
#ifdef EMBEDDED_BUILD
#define portUSING_MPU_WRAPPERS 1
#else
#define portUSING_MPU_WRAPPERS 0
#endif
