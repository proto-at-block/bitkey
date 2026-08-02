#pragma once

#include "FreeRTOS.h"
#include "semphr.h"

#include <stdbool.h>

#define RTOS_SEMAPHORE_TIMEOUT_MAX UINT32_MAX

typedef struct {
  SemaphoreHandle_t handle;
  StaticSemaphore_t buffer;
} rtos_semaphore_t;

#if defined(USE_SYSVIEW) && (USE_SYSVIEW == 1)

void _rtos_semaphore_create_impl(rtos_semaphore_t* semaphore);
void _rtos_semaphore_create_counting_impl(rtos_semaphore_t* semaphore, uint32_t max_count,
                                          uint32_t initial_count);

/* Register the semaphore handle with SystemView so it appears by name in the
   timeline rather than as a raw address. */
#include "rtos_sysview.h"
#define _RTOS_SEMAPHORE_NAME(semaphore, name_str) \
  rtos_sysview_register_resource((uint32_t)(uintptr_t)(semaphore)->handle, (name_str))
#define rtos_semaphore_create_named(semaphore, name_str) \
  do {                                                   \
    _rtos_semaphore_create_impl(semaphore);              \
    _RTOS_SEMAPHORE_NAME((semaphore), (name_str));       \
  } while (0)
#define rtos_semaphore_create(semaphore) rtos_semaphore_create_named((semaphore), #semaphore)
#define rtos_semaphore_create_counting_named(semaphore, max_count, initial_count, name_str) \
  do {                                                                                      \
    _rtos_semaphore_create_counting_impl((semaphore), (max_count), (initial_count));        \
    _RTOS_SEMAPHORE_NAME((semaphore), (name_str));                                          \
  } while (0)
#define rtos_semaphore_create_counting(semaphore, max_count, initial_count) \
  rtos_semaphore_create_counting_named((semaphore), (max_count), (initial_count), #semaphore)

bool rtos_semaphore_give_from_isr_woken(rtos_semaphore_t* semaphore, bool* wokenp);

#else

void rtos_semaphore_create(rtos_semaphore_t* semaphore);
void rtos_semaphore_create_counting(rtos_semaphore_t* semaphore, uint32_t max_count,
                                    uint32_t initial_count);

#endif

void rtos_semaphore_destroy(rtos_semaphore_t* semaphore);
bool rtos_semaphore_take(rtos_semaphore_t* semaphore, uint32_t timeout_ms);
bool rtos_semaphore_take_ticks(rtos_semaphore_t* semaphore, uint32_t ticks);

bool rtos_semaphore_give(rtos_semaphore_t* semaphore);

bool rtos_semaphore_take_from_isr(rtos_semaphore_t* semaphore);
bool rtos_semaphore_give_from_isr(rtos_semaphore_t* semaphore);
