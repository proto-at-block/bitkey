#pragma once

#include "FreeRTOS.h"
#include "queue.h"

#include <stdbool.h>

#define RTOS_QUEUE_TIMEOUT_MAX UINT32_MAX

typedef struct {
  QueueHandle_t handle;
} rtos_queue_t;

// TODO(W-4581)
#define rtos_queue_create(name, item_type, queue_length)                          \
  ({                                                                              \
    static item_type _##name##_buffer[queue_length * sizeof(item_type)] = {0};    \
    static SHARED_TASK_DATA rtos_queue_t _##name##_queue = {0};                   \
    static StaticQueue_t _##name##_static_queue = {0};                            \
    _rtos_queue_create_static(&_##name##_queue, sizeof(item_type), queue_length,  \
                              (void*)&_##name##_buffer, &_##name##_static_queue); \
    &_##name##_queue;                                                             \
  })

// Send/recv an object to/from the a queue. The object is copied, not sent by reference!
// Don't call from an ISR.
bool rtos_queue_send(rtos_queue_t* queue, void* object, uint32_t timeout_ms);
bool rtos_queue_recv(rtos_queue_t* queue, void* object, uint32_t timeout_ms);

// Don't use -- use rtos_queue_create instead.
void _rtos_queue_create_static(rtos_queue_t* queue, uint32_t item_size, uint32_t length,
                               void* buffer, StaticQueue_t* static_queue);
