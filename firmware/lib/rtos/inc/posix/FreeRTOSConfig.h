/**
 * @file FreeRTOSConfig.h
 * @brief FreeRTOS config for POSIX builds
 */

#pragma once

/* Clock and timing */
#define configCPU_CLOCK_HZ 80000000
#define configTICK_RATE_HZ 1000

/* Feature enables */
#define configSUPPORT_STATIC_ALLOCATION  1
#define configSUPPORT_DYNAMIC_ALLOCATION 1
#define configUSE_PREEMPTION             1
#define configUSE_TIMERS                 1
#define configUSE_MUTEXES                1
#define configUSE_RECURSIVE_MUTEXES      1
#define configUSE_COUNTING_SEMAPHORES    1
#define configUSE_TASK_NOTIFICATIONS     1
#define configUSE_TRACE_FACILITY         1
#define configUSE_16_BIT_TICKS           0

/* Task configuration */
#define configMAX_PRIORITIES     5
#define configMINIMAL_STACK_SIZE 160

/* MPU disabled on POSIX */
#define configENABLE_MPU       0
#define portUSING_MPU_WRAPPERS 0
