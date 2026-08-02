#pragma once

#include "FreeRTOS.h"
#include "queue.h"

#include <stdbool.h>

#define RTOS_QUEUE_TIMEOUT_MAX UINT32_MAX

typedef struct {
  QueueHandle_t handle;
} rtos_queue_t;

#include "rtos_sysview.h"
#define _RTOS_QUEUE_NAME(handle, name_str) \
  rtos_sysview_register_resource((uint32_t)(uintptr_t)(handle), name_str)

// TODO(W-4581)
#define rtos_queue_create(name, item_type, queue_length)                          \
  ({                                                                              \
    static item_type _##name##_buffer[queue_length] = {0};                        \
    static SHARED_TASK_BSS rtos_queue_t _##name##_queue = {0};                    \
    static StaticQueue_t _##name##_static_queue = {0};                            \
    _rtos_queue_create_static(&_##name##_queue, sizeof(item_type), queue_length,  \
                              (void*)&_##name##_buffer, &_##name##_static_queue); \
    _RTOS_QUEUE_NAME(_##name##_queue.handle, #name);                              \
    &_##name##_queue;                                                             \
  })

// Send/recv an object to/from the a queue. The object is copied, not sent by reference!
// Don't call from an ISR.
bool rtos_queue_send(rtos_queue_t* queue, void* object, uint32_t timeout_ms);
bool rtos_queue_recv(rtos_queue_t* queue, void* object, uint32_t timeout_ms);

// Don't use -- use rtos_queue_create instead.
void _rtos_queue_create_static(rtos_queue_t* queue, uint32_t item_size, uint32_t length,
                               void* buffer, StaticQueue_t* static_queue);
