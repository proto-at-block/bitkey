#include "assert.h"
#include "criterion_test_utils.h"
#include "sleep.h"

#include <criterion/criterion.h>

#include <string.h>

// Fake timer implementation
static uint32_t global_time_ms = 0;
static bool power_down_called = false;

typedef struct {
  uint32_t start;
  uint32_t end;
  uint32_t duration;
  bool expired;
  bool active;
} fake_timer_handle_t;

typedef void (*rtos_timer_callback_t)(void*);

typedef struct {
  const char* const name;
  fake_timer_handle_t handle;
  rtos_timer_callback_t callback;
} rtos_timer_t;

static rtos_timer_t* g_timer = NULL;

void rtos_mutex_create(void* UNUSED(mutex)) {}
bool rtos_mutex_lock(void* UNUSED(t)) {
  return true;
}
bool rtos_mutex_unlock(void* UNUSED(a)) {
  return true;
}

void rtos_timer_create_static(rtos_timer_t* timer, rtos_timer_callback_t callback) {
  ASSERT(timer);
  timer->callback = callback;
  timer->handle.active = false;
  timer->handle.expired = false;
  g_timer = timer;
}

void rtos_timer_start(rtos_timer_t* timer, uint32_t duration_ms) {
  ASSERT(timer);
  timer->handle.start = global_time_ms;
  timer->handle.end = global_time_ms + duration_ms;
  timer->handle.duration = duration_ms;
  timer->handle.expired = false;
  timer->handle.active = true;
}

void rtos_timer_stop(rtos_timer_t* timer) {
  ASSERT(timer);
  timer->handle.active = false;
}

bool rtos_timer_expired(rtos_timer_t* timer) {
  return timer->handle.expired;
}

static void advance_time_ms(uint32_t time_ms) {
  global_time_ms += time_ms;

  if (g_timer && g_timer->handle.active) {
    if (global_time_ms >= g_timer->handle.end) {
      g_timer->handle.expired = true;
      g_timer->callback(NULL);
    }
  }
}

static void power_down_callback(void* UNUSED(arg)) {
  power_down_called = true;
}

static void init(void) {
  global_time_ms = 0;
  power_down_called = false;
  g_timer = NULL;
  sleep_init(power_down_callback);
}

Test(sleep_test, start_stop, .init = init) {
  // Init doesn't start timer
  cr_assert_eq(g_timer->handle.active, false);

  // Start begins countdown
  sleep_start_power_timer();
  cr_assert(g_timer->handle.active);
  cr_assert_eq(g_timer->handle.duration, POWER_TIMEOUT_MS);

  // Stop halts it
  sleep_stop_power_timer();
  cr_assert_eq(g_timer->handle.active, false);
}

Test(sleep_test, timeout_fires_callback, .init = init) {
  sleep_start_power_timer();

  advance_time_ms(POWER_TIMEOUT_MS - 1);
  cr_assert_eq(power_down_called, false);

  advance_time_ms(2);
  cr_assert(power_down_called);
}

Test(sleep_test, refresh, .init = init) {
  // Refresh when not running is no-op
  sleep_refresh_power_timer();
  cr_assert_eq(g_timer->handle.active, false);

  // Start and advance partially
  sleep_start_power_timer();
  advance_time_ms(30000);
  cr_assert_eq(power_down_called, false);

  // Refresh resets timer
  sleep_refresh_power_timer();
  cr_assert_eq(g_timer->handle.end, global_time_ms + POWER_TIMEOUT_MS);
}

Test(sleep_test, inhibit, .init = init) {
  // Inhibit without timer running doesn't start it
  sleep_inhibit(30000);
  cr_assert_eq(g_timer->handle.active, false);
  cr_assert_eq(sleep_get_configured_timeout(), POWER_TIMEOUT_MS + 30000);

  // Start timer - uses inhibited timeout
  sleep_start_power_timer();
  cr_assert_eq(g_timer->handle.duration, POWER_TIMEOUT_MS + 30000);

  // Multiple inhibits overwrite (don't accumulate) and restart timer
  sleep_inhibit(10000);
  cr_assert_eq(sleep_get_configured_timeout(), POWER_TIMEOUT_MS + 10000);
  cr_assert_eq(g_timer->handle.duration, POWER_TIMEOUT_MS + 10000);

  // Clear inhibit resets to base and restarts timer
  sleep_clear_inhibit();
  cr_assert_eq(sleep_get_configured_timeout(), POWER_TIMEOUT_MS);
  cr_assert_eq(g_timer->handle.duration, POWER_TIMEOUT_MS);

  // Stop clears inhibit
  sleep_inhibit(30000);
  sleep_stop_power_timer();
  cr_assert_eq(sleep_get_configured_timeout(), POWER_TIMEOUT_MS);
}

Test(sleep_test, inhibit_restarts_from_current_time, .init = init) {
  sleep_start_power_timer();

  // Advance partially into the timeout
  advance_time_ms(20000);
  cr_assert_eq(power_down_called, false);

  // Inhibit restarts timer from current time with extended duration
  sleep_inhibit(30000);
  uint32_t expected_end = global_time_ms + POWER_TIMEOUT_MS + 30000;
  cr_assert_eq(g_timer->handle.end, expected_end);

  // Original timeout would have been at 60000, but now it's 20000 + 90000 = 110000
  advance_time_ms(POWER_TIMEOUT_MS - 1);  // At 79999ms
  cr_assert_eq(power_down_called, false);

  advance_time_ms(30000);  // At 109999ms - still not fired
  cr_assert_eq(power_down_called, false);

  advance_time_ms(2);  // At 110001ms - should fire
  cr_assert(power_down_called);
}
