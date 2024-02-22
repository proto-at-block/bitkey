#include "assert.h"
#include "rtos.h"
#include "timers.h"

#define NONBLOCKING ((TickType_t)1)

void rtos_timer_create_static(rtos_timer_t* timer, rtos_timer_callback_t callback) {
  ASSERT(timer);
  timer->handle =
    xTimerCreateStatic(timer->name, (TickType_t)1, pdFALSE, NULL, callback, &timer->buffer);
  timer->active = false;
  ASSERT(timer->handle);
}

bool rtos_timer_expired(rtos_timer_t* timer) {
  ASSERT(timer && timer->handle);
  return (xTimerIsTimerActive(timer->handle) == pdFALSE);
}

void rtos_timer_start(rtos_timer_t* timer, uint32_t duration_ms) {
  ASSERT(timer && timer->handle);
  BaseType_t ret = xTimerChangePeriod(timer->handle, duration_ms / portTICK_PERIOD_MS, NONBLOCKING);
  ASSERT(ret != pdFAIL);
  timer->active = true;
}

void rtos_timer_stop(rtos_timer_t* timer) {
  ASSERT(timer && timer->handle);
  xTimerStop(timer->handle, NONBLOCKING);
  timer->active = false;
}

void rtos_timer_restart(rtos_timer_t* timer) {
  ASSERT(timer && timer->handle);
  ASSERT(xTimerReset(timer->handle, NONBLOCKING) == pdTRUE);
}

uint32_t rtos_timer_remaining_ms(rtos_timer_t* timer) {
  ASSERT(timer && timer->handle);

  if (!timer->active) {
    return 0;
  }

  TickType_t remaining_time = xTimerGetExpiryTime(timer->handle) - xTaskGetTickCount();
  return TICKS2MS(remaining_time);
}
