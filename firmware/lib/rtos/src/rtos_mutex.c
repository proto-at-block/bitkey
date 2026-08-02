#include "assert.h"
#include "rtos.h"
#include "semphr.h"

#if defined(USE_SYSVIEW) && (USE_SYSVIEW == 1)
#include "rtos_sysview_trace.h"

extern char ram_addr[];

static uint32_t rtos_sysview_trace_mutex_id(const rtos_mutex_t* mutex) {
  /* Must match SEGGER_SYSVIEW_SetRAMBase() in rtos_sysview.c and
     SEGGER_SYSVIEW_ID_SHIFT (== 2) in SEGGER_SYSVIEW_Conf.h. */
  return (((uint32_t)(uintptr_t)mutex->handle) - (uint32_t)(uintptr_t)ram_addr) >> 2u;
}
#endif

#if defined(USE_SYSVIEW) && (USE_SYSVIEW == 1)
// The public symbol is a macro in rtos_mutex.h that calls this and then names
// the resource for SystemView.
#undef rtos_mutex_create
void _rtos_mutex_create_impl(rtos_mutex_t* mutex) {
#else
void rtos_mutex_create(rtos_mutex_t* mutex) {
#endif
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

#if defined(USE_SYSVIEW) && (USE_SYSVIEW == 1)
  const uint32_t mutex_id = rtos_sysview_trace_mutex_id(mutex);
  SEGGER_SYSVIEW_RecordU32(SYSVIEW_PERIPHERALS_mutex_lock, mutex_id);
  const bool result = xSemaphoreTake((xSemaphoreHandle)mutex->handle, portMAX_DELAY) == pdTRUE;
  SEGGER_SYSVIEW_RecordEndCallU32(SYSVIEW_PERIPHERALS_mutex_lock, result);
  return result;
#else
  return xSemaphoreTake((xSemaphoreHandle)mutex->handle, portMAX_DELAY) == pdTRUE;
#endif
}

bool rtos_mutex_unlock(rtos_mutex_t* mutex) {
  ASSERT(mutex->handle != NULL);

#if defined(USE_SYSVIEW) && (USE_SYSVIEW == 1)
  const uint32_t mutex_id = rtos_sysview_trace_mutex_id(mutex);
  SEGGER_SYSVIEW_RecordU32(SYSVIEW_PERIPHERALS_mutex_unlock, mutex_id);
  const bool result = xSemaphoreGive((xSemaphoreHandle)mutex->handle) == pdTRUE;
  SEGGER_SYSVIEW_RecordEndCallU32(SYSVIEW_PERIPHERALS_mutex_unlock, result);
  return result;
#else
  return xSemaphoreGive((xSemaphoreHandle)mutex->handle) == pdTRUE;
#endif
}

bool rtos_mutex_take(rtos_mutex_t* mutex, uint32_t timeout_ms) {
  ASSERT(mutex->handle != NULL);

  portTickType timeout_ticks;
  if (timeout_ms == RTOS_SEMAPHORE_TIMEOUT_MAX)
    timeout_ticks = portMAX_DELAY;
  else
    timeout_ticks = MS2TICKS(timeout_ms);

#if defined(USE_SYSVIEW) && (USE_SYSVIEW == 1)
  const uint32_t mutex_id = rtos_sysview_trace_mutex_id(mutex);
  SEGGER_SYSVIEW_RecordU32x2(SYSVIEW_PERIPHERALS_mutex_take, mutex_id, timeout_ms);
  const bool result = xSemaphoreTake(mutex->handle, timeout_ticks) == pdTRUE;
  SEGGER_SYSVIEW_RecordEndCallU32(SYSVIEW_PERIPHERALS_mutex_take, result);
  return result;
#else
  return xSemaphoreTake(mutex->handle, timeout_ticks) == pdTRUE;
#endif
}

bool rtos_mutex_lock_from_isr(rtos_mutex_t* mutex) {
  ASSERT(mutex->handle != NULL);

#if defined(USE_SYSVIEW) && (USE_SYSVIEW == 1)
  const uint32_t mutex_id = rtos_sysview_trace_mutex_id(mutex);
  SEGGER_SYSVIEW_RecordU32(SYSVIEW_PERIPHERALS_mutex_lock_from_isr, mutex_id);
  const bool result = xSemaphoreTakeFromISR((xSemaphoreHandle)mutex->handle, NULL) == pdTRUE;
  SEGGER_SYSVIEW_RecordEndCallU32(SYSVIEW_PERIPHERALS_mutex_lock_from_isr, result);
  return result;
#else
  return xSemaphoreTakeFromISR((xSemaphoreHandle)mutex->handle, NULL) == pdTRUE;
#endif
}

bool rtos_mutex_unlock_from_isr(rtos_mutex_t* mutex) {
  ASSERT(mutex->handle != NULL);

#if defined(USE_SYSVIEW) && (USE_SYSVIEW == 1)
  const uint32_t mutex_id = rtos_sysview_trace_mutex_id(mutex);
  SEGGER_SYSVIEW_RecordU32(SYSVIEW_PERIPHERALS_mutex_unlock_from_isr, mutex_id);
  const bool result = xSemaphoreGiveFromISR(mutex->handle, NULL) == pdTRUE;
  SEGGER_SYSVIEW_RecordEndCallU32(SYSVIEW_PERIPHERALS_mutex_unlock_from_isr, result);
  return result;
#else
  return xSemaphoreGiveFromISR(mutex->handle, NULL) == pdTRUE;
#endif
}

bool rtos_mutex_owner(rtos_mutex_t* mutex) {
  return xSemaphoreGetMutexHolder((xSemaphoreHandle)mutex->handle) == xTaskGetCurrentTaskHandle();
}
