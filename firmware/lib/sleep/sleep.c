#include "sleep.h"

#include "attributes.h"
#include "rtos.h"

static struct {
  rtos_timer_t power_timer;
  rtos_mutex_t lock;
  uint32_t power_timeout_ms;
} sleep_ctx SHARED_TASK_DATA = {
  .power_timer =
    {
      .name = "power",
    },
  .lock = {0},
  .power_timeout_ms = DEFAULT_POWER_TIMEOUT_MS,
};

void sleep_init(sleep_timer_callback_t callback) {
  rtos_timer_create_static(&sleep_ctx.power_timer, (rtos_timer_callback_t)callback);
  rtos_mutex_create(&sleep_ctx.lock);
  // NOTE: A task must start the timer.
}

void sleep_set_power_timeout(uint32_t timeout) {
  rtos_mutex_lock(&sleep_ctx.lock);

  sleep_ctx.power_timeout_ms = timeout;
  rtos_timer_stop(&sleep_ctx.power_timer);
  rtos_timer_start(&sleep_ctx.power_timer, sleep_ctx.power_timeout_ms);

  rtos_mutex_unlock(&sleep_ctx.lock);
}

uint32_t sleep_get_power_timeout(void) {
  return sleep_ctx.power_timeout_ms;
}

void sleep_refresh_power_timer() {
  rtos_mutex_lock(&sleep_ctx.lock);

  rtos_timer_stop(&sleep_ctx.power_timer);
  rtos_timer_start(&sleep_ctx.power_timer, sleep_ctx.power_timeout_ms);

  rtos_mutex_unlock(&sleep_ctx.lock);
}
