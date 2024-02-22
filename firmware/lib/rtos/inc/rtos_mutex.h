#pragma once

#include "FreeRTOS.h"
#include "semphr.h"

#include <stdbool.h>

typedef struct {
  SemaphoreHandle_t handle;
  StaticSemaphore_t buffer;
} rtos_mutex_t;

void rtos_mutex_create(rtos_mutex_t* mutex);
bool rtos_mutex_lock(rtos_mutex_t* mutex);
bool rtos_mutex_unlock(rtos_mutex_t* mutex);
bool rtos_mutex_take(rtos_mutex_t* mutex, uint32_t timeout_ms);
