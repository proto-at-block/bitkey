/**
 * @file rtos_thread.h
 * @brief POSIX stub for rtos_thread.h - thread management
 *
 * Replaces lib/rtos/inc/rtos_thread.h for POSIX builds.
 */

#pragma once

#include "FreeRTOS.h"
#include "rtos_mpu.h"

#include <stdalign.h>
#include <stdbool.h>
#include <stdint.h>

#define RTOS_THREAD_TIMEOUT_MAX         UINT32_MAX
#define RTOS_STATIC_STACK_DEPTH_DEFAULT 512U

#define RTOS_DEADLINE(start, ms) (rtos_thread_systime() - (start) > (ms))

typedef enum {
  RTOS_THREAD_PRIORITY_LOW = 1,
  RTOS_THREAD_PRIORITY_NORMAL = 2,
  RTOS_THREAD_PRIORITY_HIGH = 3,
  RTOS_THREAD_PRIORITY_HIGHEST = 4,
} rtos_thread_priority_t;

typedef struct {
  uintptr_t handle;
} rtos_thread_t;

/* Thread creation - implemented in rtos_posix.c */
void rtos_thread_create_static(rtos_thread_t* thread, void (*func)(void*), const char* name,
                               void* args, rtos_thread_priority_t priority, uint32_t* stack_buffer,
                               uint32_t stack_size, StaticTask_t* task_buffer,
                               rtos_thread_mpu_t mpu_regions);

/* Convenience macro for thread creation (allocates static storage) */
#define rtos_thread_create(func, args, priority, stack_size)                                      \
  ({                                                                                              \
    static rtos_thread_t _##func##_thread = {0};                                                  \
    static StaticTask_t _##func##_task_buffer = {0};                                              \
    alignas(stack_size) static uint32_t _##func##_stack_buffer[stack_size / sizeof(uint32_t)] = { \
      0};                                                                                         \
    static rtos_thread_mpu_t _##func##_regions = {.privilege = 0};                                \
    rtos_thread_create_static(&_##func##_thread, func, "" #func "", args, priority,               \
                              _##func##_stack_buffer, stack_size, &_##func##_task_buffer,         \
                              _##func##_regions);                                                 \
    &_##func##_thread;                                                                            \
  })

void rtos_thread_delete(rtos_thread_t* thread);
void rtos_thread_start_scheduler(void);
void rtos_thread_sleep(uint32_t time_ms);
void rtos_thread_sleep_until(uint32_t* last_wake_time_ms, uint32_t period_ms);
uint32_t rtos_thread_systime(void);
uint64_t rtos_thread_micros(void);
bool rtos_in_isr(void);

/* Critical sections - no-op on POSIX */
#define rtos_thread_enter_critical taskENTER_CRITICAL
#define rtos_thread_exit_critical  taskEXIT_CRITICAL
