#pragma once

#include "FreeRTOS.h"
#include "semphr.h"

#include <stdbool.h>

#define RTOS_SEMAPHORE_TIMEOUT_MAX UINT32_MAX

typedef struct {
  SemaphoreHandle_t handle;
  StaticSemaphore_t buffer;
} rtos_semaphore_t;

void rtos_semaphore_create(rtos_semaphore_t* semaphore);
void rtos_semaphore_create_counting(rtos_semaphore_t* semaphore, uint32_t max_count,
                                    uint32_t initial_count);
bool rtos_semaphore_take(rtos_semaphore_t* semaphore, uint32_t timeout_ms);
bool rtos_semaphore_take_ticks(rtos_semaphore_t* semaphore, uint32_t ticks);

bool rtos_semaphore_give(rtos_semaphore_t* semaphore);

bool rtos_semaphore_take_from_isr(rtos_semaphore_t* semaphore);
bool rtos_semaphore_give_from_isr(rtos_semaphore_t* semaphore);
