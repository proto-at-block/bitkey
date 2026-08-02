/**
 * @file FreeRTOS.h
 * @brief FreeRTOS type stubs for POSIX builds
 *
 * Provides type definitions to satisfy firmware header includes.
 * Actual RTOS functionality is implemented in rtos_posix.c using pthreads.
 *
 * Note: This header also satisfies includes of event_groups.h, queue.h,
 * semphr.h, task.h, and timers.h - those files simply include this one.
 */

#pragma once

#include "FreeRTOSConfig.h"

#include <stddef.h>
#include <stdint.h>
#include <stdlib.h>

/* Basic FreeRTOS config defines */
#define portTICK_RATE_MS             1
#define portTICK_PERIOD_MS           1
#define portMAX_DELAY                0xFFFFFFFFUL
#define portNUM_CONFIGURABLE_REGIONS 3
/* MPU privilege bit - meaningless on POSIX (all threads privileged) */
#define portPRIVILEGE_BIT (0UL)

/* Basic types */
typedef uint32_t TickType_t;
typedef int32_t BaseType_t;
typedef uint32_t UBaseType_t;
typedef uint32_t EventBits_t;
typedef uint32_t StackType_t;
typedef void* TaskHandle_t;
typedef void* QueueHandle_t;
typedef void* SemaphoreHandle_t;
typedef void* EventGroupHandle_t;
typedef void* TimerHandle_t;
typedef void (*TaskFunction_t)(void*);

/**
 * Static allocation types - sized to hold POSIX pthread primitives.
 *
 * Size requirements (macOS):
 * - pthread_mutex_t: 64 bytes
 * - pthread_cond_t:  48 bytes
 * - posix_queue_t:       mutex + 2*cond + pointers = ~208 bytes
 * - posix_semaphore_t:   mutex + cond + int        = ~120 bytes
 * - posix_mutex_t:       mutex + cond + pthread_t  = ~128 bytes
 * - posix_event_group_t: mutex + cond + uint32     = ~120 bytes
 */
typedef struct {
  uint8_t data[128];
} StaticTask_t;
typedef struct {
  uint8_t data[256];
} StaticQueue_t;
typedef struct {
  uint8_t data[144];
} StaticSemaphore_t;
typedef struct {
  uint8_t data[128];
} StaticEventGroup_t;
typedef struct {
  uint8_t data[64];
} StaticTimer_t;
typedef struct {
  uint32_t data[2];
} MemoryRegion_t;

/* Memory allocation - use standard malloc/free */
static inline void* pvPortMalloc(size_t size) {
  return malloc(size);
}
static inline void vPortFree(void* ptr) {
  free(ptr);
}

/* Boolean values */
#define pdTRUE  1
#define pdFALSE 0
#define pdPASS  pdTRUE
#define pdFAIL  pdFALSE

/* Critical section macros - no-op on POSIX */
#define taskENTER_CRITICAL() ((void)0)
#define taskEXIT_CRITICAL()  ((void)0)
