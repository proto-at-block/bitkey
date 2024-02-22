#include "assert.h"
#include "rtos.h"
#include "semphr.h"

void rtos_semaphore_create(rtos_semaphore_t* semaphore) {
  semaphore->handle = xSemaphoreCreateBinaryStatic(&semaphore->buffer);

  ASSERT(semaphore->handle != NULL);
}

void rtos_semaphore_create_counting(rtos_semaphore_t* semaphore, uint32_t max_count,
                                    uint32_t initial_count) {
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

  return xSemaphoreTake(semaphore->handle, timeout_ticks) == pdTRUE;
}

bool rtos_semaphore_take_ticks(rtos_semaphore_t* semaphore, uint32_t ticks) {
  ASSERT(semaphore->handle != NULL);
  return xSemaphoreTake(semaphore->handle, (portTickType)ticks) == pdTRUE;
}

bool rtos_semaphore_give(rtos_semaphore_t* semaphore) {
  ASSERT(semaphore != NULL);

  return xSemaphoreGive(semaphore->handle) == pdTRUE;
}

bool rtos_semaphore_take_from_isr(rtos_semaphore_t* semaphore) {
  ASSERT(semaphore != NULL);

  return xSemaphoreTakeFromISR(semaphore->handle, NULL) == pdTRUE;
}

bool rtos_semaphore_give_from_isr(rtos_semaphore_t* semaphore) {
  ASSERT(semaphore != NULL);

  return xSemaphoreGiveFromISR(semaphore->handle, NULL) == pdTRUE;
}
