#pragma once

#include "FreeRTOS.h"
#include "rtos_mpu.h"
#include "semphr.h"

#include <stdalign.h>
#include <stdint.h>
#include <strings.h>

#define RTOS_THREAD_TIMEOUT_MAX UINT32_MAX

#define RTOS_DEADLINE(start, ms) (rtos_thread_systime() - start > ms)

typedef enum {
  RTOS_THREAD_PRIORITY_LOW = 1,
  RTOS_THREAD_PRIORITY_NORMAL = 2,
  RTOS_THREAD_PRIORITY_HIGH = 3,
  RTOS_THREAD_PRIORITY_HIGHEST = 4, /* this has to match (configMAX_PRIORITIES - 1) */
} rtos_thread_priority_t;

typedef struct {
  uintptr_t handle;
} rtos_thread_t;

#define RTOS_STATIC_STACK_DEPTH_DEFAULT (512U)

/* Thread creation macro declares all the necessary static variables for the task */
/* IMPORTANT: stack_size is in *bytes*, not words! */
void rtos_thread_create_static(rtos_thread_t* thread, void (*func)(void*), const char* name,
                               void* args, rtos_thread_priority_t priority, uint32_t* stack_buffer,
                               uint32_t stack_size, StaticTask_t* task_buffer,
                               rtos_thread_mpu_t mpu_regions);

#define rtos_thread_create(func, args, priority, stack_size)                                      \
  ({                                                                                              \
    static rtos_thread_t _##func##_thread = {0};                                                  \
    static StaticTask_t _##func##_task_buffer = {0};                                              \
    alignas(stack_size) static uint32_t _##func##_stack_buffer[stack_size / sizeof(uint32_t)] = { \
      0};                                                                                         \
    rtos_thread_create_static(&_##func##_thread, func, "" #func "", args, priority,               \
                              _##func##_stack_buffer, stack_size, &_##func##_task_buffer,         \
                              _##func##_regions);                                                 \
    &_##func##_thread;                                                                            \
  })

void rtos_thread_delete(rtos_thread_t* thread);
void rtos_thread_start_scheduler(void);
void rtos_thread_sleep(const uint32_t time_ms);
uint32_t rtos_thread_systime(void);
uint64_t rtos_thread_micros(void);

bool rtos_in_isr(void);

#define rtos_thread_enter_critical taskENTER_CRITICAL
#define rtos_thread_exit_critical  taskEXIT_CRITICAL
