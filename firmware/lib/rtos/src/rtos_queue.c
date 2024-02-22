#include "assert.h"
#include "rtos.h"

void _rtos_queue_create_static(rtos_queue_t* queue, uint32_t item_size, uint32_t length,
                               void* buffer, StaticQueue_t* static_queue) {
  ASSERT(queue);
  queue->handle = xQueueCreateStatic(length, item_size, buffer, static_queue);
  ASSERT(queue->handle);
}

bool rtos_queue_send(rtos_queue_t* queue, void* object, uint32_t timeout_ms) {
  ASSERT(queue && object);
  portTickType timeout_ticks;
  if (timeout_ms == RTOS_QUEUE_TIMEOUT_MAX) {
    timeout_ticks = portMAX_DELAY;
  } else {
    timeout_ticks = MS2TICKS(timeout_ms);
  }
  return xQueueSendToBack(queue->handle, object, timeout_ticks) == pdTRUE;
}

bool rtos_queue_recv(rtos_queue_t* queue, void* object, uint32_t timeout_ms) {
  ASSERT(queue && object);
  portTickType timeout_ticks;
  if (timeout_ms == RTOS_QUEUE_TIMEOUT_MAX) {
    timeout_ticks = portMAX_DELAY;
  } else {
    timeout_ticks = MS2TICKS(timeout_ms);
  }
  return xQueueReceive(queue->handle, object, timeout_ticks) == pdTRUE;
}
