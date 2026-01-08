#pragma once

#include "FreeRTOS.h"
#include "semphr.h"

#include <stdbool.h>

typedef struct {
  SemaphoreHandle_t handle;
  StaticSemaphore_t buffer;
} rtos_mutex_t;

void rtos_mutex_create(rtos_mutex_t* mutex);
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
