#pragma once

#include "FreeRTOS.h"
#include "timers.h"

typedef TimerHandle_t rtos_timer_handle_t;
typedef StaticTimer_t rtos_static_timer_t;
typedef void (*rtos_timer_callback_t)(rtos_timer_handle_t);

typedef struct {
  const char* const name;
  rtos_timer_handle_t handle;
  rtos_static_timer_t buffer;
  bool active;
} rtos_timer_t;

void rtos_timer_create_static(rtos_timer_t* timer, rtos_timer_callback_t callback);
bool rtos_timer_expired(rtos_timer_t* timer);

// Start a timer which expires after `duration_ms`.
void rtos_timer_start(rtos_timer_t* timer, uint32_t duration_ms);
void rtos_timer_stop(rtos_timer_t* timer);
void rtos_timer_restart(rtos_timer_t* timer);
uint32_t rtos_timer_remaining_ms(rtos_timer_t* timer);
