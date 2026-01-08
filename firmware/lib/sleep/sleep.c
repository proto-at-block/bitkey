#include "sleep.h"

#include "attributes.h"
#include "rtos.h"

static struct {
  rtos_timer_t power_timer;
  rtos_mutex_t lock;
  bool timer_running;
  uint32_t inhibit_duration_ms;
} sleep_ctx SHARED_TASK_DATA = {
  .power_timer =
    {
      .name = "power",
    },
  .lock = {0},
  .timer_running = false,
  .inhibit_duration_ms = 0,
};

static uint32_t get_timeout_ms(void) {
  return POWER_TIMEOUT_MS + sleep_ctx.inhibit_duration_ms;
}

void sleep_init(sleep_timer_callback_t callback) {
  rtos_timer_create_static(&sleep_ctx.power_timer, (rtos_timer_callback_t)callback);
  rtos_mutex_create(&sleep_ctx.lock);
  // NOTE: A task must call sleep_start_power_timer() to start countdown.
}

void sleep_start_power_timer(void) {
  rtos_mutex_lock(&sleep_ctx.lock);

  sleep_ctx.timer_running = true;
  uint32_t timeout_ms = get_timeout_ms();
  rtos_timer_stop(&sleep_ctx.power_timer);
  rtos_timer_start(&sleep_ctx.power_timer, timeout_ms);

  rtos_mutex_unlock(&sleep_ctx.lock);
}

void sleep_stop_power_timer(void) {
  rtos_mutex_lock(&sleep_ctx.lock);

  sleep_ctx.timer_running = false;
  rtos_timer_stop(&sleep_ctx.power_timer);
  sleep_ctx.inhibit_duration_ms = 0;  // Clear inhibit when stopping

  rtos_mutex_unlock(&sleep_ctx.lock);
}

void sleep_refresh_power_timer(void) {
  rtos_mutex_lock(&sleep_ctx.lock);

  // Only refresh if timer is running (locked state)
  if (sleep_ctx.timer_running) {
    uint32_t timeout_ms = get_timeout_ms();
    rtos_timer_stop(&sleep_ctx.power_timer);
    rtos_timer_start(&sleep_ctx.power_timer, timeout_ms);
  }

  rtos_mutex_unlock(&sleep_ctx.lock);
}

void sleep_inhibit(uint32_t additional_ms) {
  rtos_mutex_lock(&sleep_ctx.lock);

  sleep_ctx.inhibit_duration_ms = additional_ms;

  // Restart timer with new timeout if running
  if (sleep_ctx.timer_running) {
    uint32_t timeout_ms = get_timeout_ms();
    rtos_timer_stop(&sleep_ctx.power_timer);
    rtos_timer_start(&sleep_ctx.power_timer, timeout_ms);
  }

  rtos_mutex_unlock(&sleep_ctx.lock);
}

void sleep_clear_inhibit(void) {
  rtos_mutex_lock(&sleep_ctx.lock);

  sleep_ctx.inhibit_duration_ms = 0;

  // Restart timer with new timeout if running
  if (sleep_ctx.timer_running) {
    uint32_t timeout_ms = get_timeout_ms();
    rtos_timer_stop(&sleep_ctx.power_timer);
    rtos_timer_start(&sleep_ctx.power_timer, timeout_ms);
  }

  rtos_mutex_unlock(&sleep_ctx.lock);
}

uint32_t sleep_get_configured_timeout(void) {
  rtos_mutex_lock(&sleep_ctx.lock);
  uint32_t timeout_ms = get_timeout_ms();
  rtos_mutex_unlock(&sleep_ctx.lock);
  return timeout_ms;
}
