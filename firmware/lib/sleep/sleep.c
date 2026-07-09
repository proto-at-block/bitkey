#include "sleep.h"

#include "attributes.h"
#include "log.h"
#include "rtos.h"

static struct {
  rtos_timer_t power_timer;
  rtos_mutex_t lock;
  bool timer_running;
  uint32_t inhibit_duration_ms;
  uint32_t charger_extra_ms;
  // Latched true when the shutdown sequence has begun. Once set, the timer
  // cannot be re-armed — all start/refresh/inhibit/charger-extension calls
  // become no-ops. Never cleared (only a reset clears it, by reinitializing
  // BSS).
  bool shutting_down;
} sleep_ctx SHARED_TASK_DATA = {
  .power_timer =
    {
      .name = "power",
    },
  .lock = {0},
  .timer_running = false,
  .inhibit_duration_ms = 0,
  .charger_extra_ms = 0,
  .shutting_down = false,
};

static uint32_t get_timeout_ms(void) {
  if (sleep_ctx.inhibit_duration_ms == SLEEP_INHIBIT_INFINITE) {
    return SLEEP_INHIBIT_INFINITE;
  }
  return POWER_TIMEOUT_MS + sleep_ctx.inhibit_duration_ms + sleep_ctx.charger_extra_ms;
}

void sleep_init(sleep_timer_callback_t callback) {
  rtos_timer_create_static(&sleep_ctx.power_timer, (rtos_timer_callback_t)callback);
  rtos_mutex_create(&sleep_ctx.lock);
  // NOTE: A task must call sleep_start_power_timer() to start countdown.
}

void sleep_start_power_timer(void) {
  rtos_mutex_lock(&sleep_ctx.lock);

  if (sleep_ctx.shutting_down) {
    rtos_mutex_unlock(&sleep_ctx.lock);
    return;
  }

  sleep_ctx.timer_running = true;
  uint32_t timeout_ms = get_timeout_ms();
  rtos_timer_stop(&sleep_ctx.power_timer);
  rtos_timer_start(&sleep_ctx.power_timer, timeout_ms);
  MFLOGI("power timer start %lu ms", (unsigned long)timeout_ms);

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

  // Once the shutdown sequence has begun, no event may re-arm the timer.
  // Only refresh if timer is running (locked state) and not shutting down.
  if (sleep_ctx.timer_running && !sleep_ctx.shutting_down) {
    uint32_t timeout_ms = get_timeout_ms();
    rtos_timer_stop(&sleep_ctx.power_timer);
    rtos_timer_start(&sleep_ctx.power_timer, timeout_ms);
  }

  rtos_mutex_unlock(&sleep_ctx.lock);
}

void sleep_inhibit(uint32_t additional_ms) {
  rtos_mutex_lock(&sleep_ctx.lock);

  if (sleep_ctx.shutting_down) {
    rtos_mutex_unlock(&sleep_ctx.lock);
    return;
  }

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

  if (sleep_ctx.shutting_down) {
    rtos_mutex_unlock(&sleep_ctx.lock);
    return;
  }

  sleep_ctx.inhibit_duration_ms = 0;

  // Restart timer with new timeout if running
  if (sleep_ctx.timer_running) {
    uint32_t timeout_ms = get_timeout_ms();
    rtos_timer_stop(&sleep_ctx.power_timer);
    rtos_timer_start(&sleep_ctx.power_timer, timeout_ms);
  }

  rtos_mutex_unlock(&sleep_ctx.lock);
}

void sleep_set_charger_extension(uint32_t extra_ms) {
  rtos_mutex_lock(&sleep_ctx.lock);

  if (sleep_ctx.shutting_down) {
    rtos_mutex_unlock(&sleep_ctx.lock);
    return;
  }

  sleep_ctx.charger_extra_ms = extra_ms;

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

void sleep_start_power_timer_with_timeout(uint32_t timeout_ms) {
  rtos_mutex_lock(&sleep_ctx.lock);

  if (sleep_ctx.shutting_down) {
    rtos_mutex_unlock(&sleep_ctx.lock);
    return;
  }

  MFLOGI("power timer start (abs) %lu ms", (unsigned long)timeout_ms);
  sleep_ctx.timer_running = true;
  sleep_ctx.inhibit_duration_ms = 0;  // Clear inhibit when using absolute timeout
  rtos_timer_stop(&sleep_ctx.power_timer);
  rtos_timer_start(&sleep_ctx.power_timer, timeout_ms);

  rtos_mutex_unlock(&sleep_ctx.lock);
}

bool sleep_begin_shutdown(void) {
  rtos_mutex_lock(&sleep_ctx.lock);

  bool first_call = !sleep_ctx.shutting_down;
  sleep_ctx.shutting_down = true;
  // Clear the stale `timer_running` bookkeeping left behind by the one-shot
  // timer so a stray refresh (even before the latch check lands) cannot
  // re-arm the timer.
  sleep_ctx.timer_running = false;
  rtos_timer_stop(&sleep_ctx.power_timer);

  rtos_mutex_unlock(&sleep_ctx.lock);

  return first_call;
}

bool sleep_is_shutting_down(void) {
  rtos_mutex_lock(&sleep_ctx.lock);
  bool value = sleep_ctx.shutting_down;
  rtos_mutex_unlock(&sleep_ctx.lock);
  return value;
}

void sleep_cancel_shutdown(void) {
  rtos_mutex_lock(&sleep_ctx.lock);
  sleep_ctx.shutting_down = false;
  // Timer remains stopped; the caller decides whether/when to restart it.
  rtos_mutex_unlock(&sleep_ctx.lock);
}
