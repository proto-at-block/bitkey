#include "assert.h"
#include "rtos.h"
#include "semphr.h"

#if defined(USE_SYSVIEW) && (USE_SYSVIEW == 1)
#include "rtos_sysview_trace.h"

extern char ram_addr[];

static uint32_t rtos_sysview_trace_semaphore_id(const rtos_semaphore_t* semaphore) {
  /* Must match SEGGER_SYSVIEW_SetRAMBase() in rtos_sysview.c and
     SEGGER_SYSVIEW_ID_SHIFT (== 2) in SEGGER_SYSVIEW_Conf.h. */
  return (((uint32_t)(uintptr_t)semaphore->handle) - (uint32_t)(uintptr_t)ram_addr) >> 2u;
}
#endif

#if defined(USE_SYSVIEW) && (USE_SYSVIEW == 1)
// Public name is a SystemView-naming macro in rtos_semaphore.h.
#undef rtos_semaphore_create
void _rtos_semaphore_create_impl(rtos_semaphore_t* semaphore) {
#else
void rtos_semaphore_create(rtos_semaphore_t* semaphore) {
#endif
  semaphore->handle = xSemaphoreCreateBinaryStatic(&semaphore->buffer);

  ASSERT(semaphore->handle != NULL);
}

void rtos_semaphore_destroy(rtos_semaphore_t* semaphore) {
  ASSERT(semaphore != NULL);

  if (semaphore->handle != NULL) {
    vSemaphoreDelete(semaphore->handle);
    semaphore->handle = NULL;
  }
}

#if defined(USE_SYSVIEW) && (USE_SYSVIEW == 1)
#undef rtos_semaphore_create_counting
void _rtos_semaphore_create_counting_impl(rtos_semaphore_t* semaphore, uint32_t max_count,
                                          uint32_t initial_count) {
#else
void rtos_semaphore_create_counting(rtos_semaphore_t* semaphore, uint32_t max_count,
                                    uint32_t initial_count) {
#endif
  semaphore->handle = xSemaphoreCreateCountingStatic(max_count, initial_count, &semaphore->buffer);

  ASSERT(semaphore->handle != NULL);
}

bool rtos_semaphore_take(rtos_semaphore_t* semaphore, uint32_t timeout_ms) {
  ASSERT(semaphore->handle != NULL);

  portTickType timeout_ticks;
  if (timeout_ms == RTOS_SEMAPHORE_TIMEOUT_MAX)
    timeout_ticks = portMAX_DELAY;
  else
    timeout_ticks = MS2TICKS(timeout_ms);

#if defined(USE_SYSVIEW) && (USE_SYSVIEW == 1)
  const uint32_t semaphore_id = rtos_sysview_trace_semaphore_id(semaphore);
  SEGGER_SYSVIEW_RecordU32x2(SYSVIEW_PERIPHERALS_sem_take, semaphore_id, timeout_ms);
  const bool result = xSemaphoreTake(semaphore->handle, timeout_ticks) == pdTRUE;
  SEGGER_SYSVIEW_RecordEndCallU32(SYSVIEW_PERIPHERALS_sem_take, result);
  return result;
#else
  return xSemaphoreTake(semaphore->handle, timeout_ticks) == pdTRUE;
#endif
}

bool rtos_semaphore_take_ticks(rtos_semaphore_t* semaphore, uint32_t ticks) {
  ASSERT(semaphore->handle != NULL);

#if defined(USE_SYSVIEW) && (USE_SYSVIEW == 1)
  const uint32_t semaphore_id = rtos_sysview_trace_semaphore_id(semaphore);
  SEGGER_SYSVIEW_RecordU32x2(SYSVIEW_PERIPHERALS_sem_take_ticks, semaphore_id, ticks);
  const bool result = xSemaphoreTake(semaphore->handle, (portTickType)ticks) == pdTRUE;
  SEGGER_SYSVIEW_RecordEndCallU32(SYSVIEW_PERIPHERALS_sem_take_ticks, result);
  return result;
#else
  return xSemaphoreTake(semaphore->handle, (portTickType)ticks) == pdTRUE;
#endif
}

bool rtos_semaphore_give(rtos_semaphore_t* semaphore) {
  ASSERT(semaphore != NULL);

#if defined(USE_SYSVIEW) && (USE_SYSVIEW == 1)
  ASSERT(semaphore->handle != NULL);
  const uint32_t semaphore_id = rtos_sysview_trace_semaphore_id(semaphore);
  SEGGER_SYSVIEW_RecordU32(SYSVIEW_PERIPHERALS_sem_give, semaphore_id);
  const bool result = xSemaphoreGive(semaphore->handle) == pdTRUE;
  SEGGER_SYSVIEW_RecordEndCallU32(SYSVIEW_PERIPHERALS_sem_give, result);
  return result;
#else
  return xSemaphoreGive(semaphore->handle) == pdTRUE;
#endif
}

bool rtos_semaphore_take_from_isr(rtos_semaphore_t* semaphore) {
  ASSERT(semaphore != NULL);

#if defined(USE_SYSVIEW) && (USE_SYSVIEW == 1)
  if (semaphore->handle == NULL) {
    return false;
  }
  const uint32_t semaphore_id = rtos_sysview_trace_semaphore_id(semaphore);
  SEGGER_SYSVIEW_RecordU32(SYSVIEW_PERIPHERALS_sem_take_from_isr, semaphore_id);
  const bool result = xSemaphoreTakeFromISR(semaphore->handle, NULL) == pdTRUE;
  SEGGER_SYSVIEW_RecordEndCallU32(SYSVIEW_PERIPHERALS_sem_take_from_isr, result);
  return result;
#else
  return xSemaphoreTakeFromISR(semaphore->handle, NULL) == pdTRUE;
#endif
}

#if defined(USE_SYSVIEW) && (USE_SYSVIEW == 1)
bool rtos_semaphore_give_from_isr_woken(rtos_semaphore_t* semaphore, bool* wokenp) {
  ASSERT(semaphore != NULL);
  if (semaphore->handle == NULL) {
    return false;
  }

  portBASE_TYPE xHigherPriorityTaskWoken = pdFALSE;
  const uint32_t semaphore_id = rtos_sysview_trace_semaphore_id(semaphore);
  const bool result = xSemaphoreGiveFromISR(semaphore->handle, &xHigherPriorityTaskWoken) == pdTRUE;
  SEGGER_SYSVIEW_RecordU32x2(SYSVIEW_PERIPHERALS_sem_give_from_isr, semaphore_id,
                             xHigherPriorityTaskWoken == pdTRUE);
  SEGGER_SYSVIEW_RecordEndCallU32(SYSVIEW_PERIPHERALS_sem_give_from_isr, result);

  if (wokenp != NULL && xHigherPriorityTaskWoken == pdTRUE) {
    *wokenp = true;
  }

  return result;
}
#endif

bool rtos_semaphore_give_from_isr(rtos_semaphore_t* semaphore) {
#if defined(USE_SYSVIEW) && (USE_SYSVIEW == 1)
  return rtos_semaphore_give_from_isr_woken(semaphore, NULL);
#else
  ASSERT(semaphore != NULL);

  return xSemaphoreGiveFromISR(semaphore->handle, NULL) == pdTRUE;
#endif
}
