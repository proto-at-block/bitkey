#include "assert.h"
#include "rtos.h"
#include "semphr.h"

void rtos_mutex_create(rtos_mutex_t* mutex) {
  mutex->handle = xSemaphoreCreateMutexStatic(&mutex->buffer);

  ASSERT(mutex->handle != NULL);
}

void rtos_mutex_destroy(rtos_mutex_t* mutex) {
  ASSERT(mutex != NULL);

  if (mutex->handle != NULL) {
    vSemaphoreDelete(mutex->handle);
    mutex->handle = NULL;
  }
}

bool rtos_mutex_lock(rtos_mutex_t* mutex) {
  ASSERT(mutex->handle != NULL);

  return xSemaphoreTake((xSemaphoreHandle)mutex->handle, portMAX_DELAY) == pdTRUE;
}

bool rtos_mutex_unlock(rtos_mutex_t* mutex) {
  ASSERT(mutex->handle != NULL);

  return xSemaphoreGive((xSemaphoreHandle)mutex->handle) == pdTRUE;
}

bool rtos_mutex_take(rtos_mutex_t* mutex, uint32_t timeout_ms) {
  ASSERT(mutex->handle != NULL);

  portTickType timeout_ticks;
  if (timeout_ms == RTOS_SEMAPHORE_TIMEOUT_MAX)
    timeout_ticks = portMAX_DELAY;
  else
    timeout_ticks = MS2TICKS(timeout_ms);

  return xSemaphoreTake(mutex->handle, timeout_ticks) == pdTRUE;
}

bool rtos_mutex_lock_from_isr(rtos_mutex_t* mutex) {
  ASSERT(mutex->handle != NULL);

  return xSemaphoreTakeFromISR((xSemaphoreHandle)mutex->handle, NULL) == pdTRUE;
}

bool rtos_mutex_unlock_from_isr(rtos_mutex_t* mutex) {
  ASSERT(mutex->handle != NULL);

  return xSemaphoreGiveFromISR(mutex->handle, NULL) == pdTRUE;
}

bool rtos_mutex_owner(rtos_mutex_t* mutex) {
  return xSemaphoreGetMutexHolder((xSemaphoreHandle)mutex->handle) == xTaskGetCurrentTaskHandle();
}
