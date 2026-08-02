#pragma once

#include "FreeRTOS.h"
#include "semphr.h"

#include <stdbool.h>

typedef struct {
  SemaphoreHandle_t handle;
  StaticSemaphore_t buffer;
} rtos_mutex_t;

#if defined(USE_SYSVIEW) && (USE_SYSVIEW == 1)

void _rtos_mutex_create_impl(rtos_mutex_t* mutex);

/* Register the mutex handle with SystemView so it appears by name in the
   timeline rather than as a raw address. */
#include "rtos_sysview.h"
#define _RTOS_MUTEX_NAME(mutex, name_str) \
  rtos_sysview_register_resource((uint32_t)(uintptr_t)(mutex)->handle, (name_str))
#define rtos_mutex_create_named(mutex, name_str) \
  do {                                           \
    _rtos_mutex_create_impl(mutex);              \
    _RTOS_MUTEX_NAME((mutex), (name_str));       \
  } while (0)
#define rtos_mutex_create(mutex) rtos_mutex_create_named((mutex), #mutex)

#else

void rtos_mutex_create(rtos_mutex_t* mutex);

#endif

void rtos_mutex_destroy(rtos_mutex_t* mutex);
bool rtos_mutex_lock(rtos_mutex_t* mutex);
bool rtos_mutex_unlock(rtos_mutex_t* mutex);
bool rtos_mutex_take(rtos_mutex_t* mutex, uint32_t timeout_ms);
bool rtos_mutex_lock_from_isr(rtos_mutex_t* mutex);
bool rtos_mutex_unlock_from_isr(rtos_mutex_t* mutex);

/**
 * @brief Returns `true` if the mutex is owned by the calling thread.
 *
 * @details This method will return `true` if the specified lock was already
 * acquired by the calling thread by a call to #rtos_mutex_lock() or
 * #rtos_mutex_take().
 *
 * @param mutex  Pointer to the RTOS mutex.
 *
 * @return `true` if calling thread owns the mutex, otherwise `false`.
 */
bool rtos_mutex_owner(rtos_mutex_t* mutex);
