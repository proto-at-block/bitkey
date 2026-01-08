#include "arithmetic.h"
#include "assert.h"
#include "criterion_test_utils.h"
#include "fff.h"
#include "secutils.h"
#include "sleep.h"
#include "unlock.h"

#include <criterion/criterion.h>

#include <stdio.h>

static int global_time_ms = 0;
static bool power_on = true;
static secure_bool_t authed = SECURE_FALSE;
static bool removed_files = false;

void rtos_mutex_create(void* UNUSED(mutex)) {}
bool rtos_mutex_lock(void* UNUSED(t)) {
  return true;
}
bool rtos_mutex_unlock(void* UNUSED(a)) {
  return true;
}
void detect_glitch(void) {}
uint16_t crypto_rand_short(void) {
  return 1;
}
uint32_t clock_get_freq(void) {
  return 1;
}
void set_authenticated(secure_bool_t auth, bool UNUSED(show_animation)) {
  authed = auth;
}

void unlock_perform_wipe_state(void) {
  removed_files = true;
}

// Fake timer implementation. Could move this to a separate library, but leaving it here
// for now.

typedef struct {
  uint32_t start;
  uint32_t end;
  uint32_t current;
  uint32_t duration;
  bool expired;
} fake_timer_handle_t;

typedef void (*rtos_timer_callback_t)(fake_timer_handle_t*);

typedef struct {
  const char* const name;
  fake_timer_handle_t* handle;
  rtos_timer_callback_t callback;
  bool active;
} rtos_timer_t;

static fake_timer_handle_t unlock_timer_handle = {0};
static fake_timer_handle_t sleep_timer_handle = {0};
static rtos_timer_t* timers[2] = {0};

extern const uint32_t delay_table[];
extern unlock_delay_status_t delay_status;

static void advance_time_ms(uint32_t time_ms) {
  global_time_ms += time_ms;

  for (int i = 0; i < ARRAY_SIZE(timers); i++) {
    rtos_timer_t* timer = timers[i];
    if (timer->active) {
      timer->handle->current = global_time_ms;
      if (timer->handle->current >= timer->handle->end) {
        timer->handle->expired = true;
        timer->callback(timer->handle);
      }
    }
  }
}

void rtos_timer_create_static(rtos_timer_t* timer, rtos_timer_callback_t callback) {
  ASSERT(timer);

  if (strcmp(timer->name, "power") == 0) {
    timer->handle = &sleep_timer_handle;
  } else if (strcmp(timer->name, "unlock") == 0) {
    timer->handle = &unlock_timer_handle;
  } else {
    ASSERT(false);
  }

  timer->callback = callback;
  timer->active = false;

  ASSERT(timer->handle);

  static int offset = 0;
  timers[offset++] = timer;
}

bool rtos_timer_expired(rtos_timer_t* timer) {
  return timer->handle->expired;
}

void rtos_timer_start(rtos_timer_t* timer, uint32_t duration_ms) {
  ASSERT(timer);
  ASSERT(timer->handle);

  timer->handle->start = global_time_ms;
  timer->handle->end = global_time_ms + duration_ms;
  timer->handle->duration = duration_ms;
  timer->handle->expired = false;
  timer->active = true;
}

void rtos_timer_stop(rtos_timer_t* timer) {
  ASSERT(timer);
  ASSERT(timer->handle);

  timer->active = false;
}

void rtos_timer_restart(rtos_timer_t* timer) {
  ASSERT(timer);
  ASSERT(timer->handle);

  timer->handle->start = global_time_ms;
  timer->handle->end = global_time_ms + timer->handle->duration;
  timer->handle->expired = false;
  timer->active = true;
}

uint32_t rtos_timer_remaining_ms(rtos_timer_t* timer) {
  if (timer->handle->end < global_time_ms) {
    return 0;
  }
  return timer->handle->end - global_time_ms;
}

static void sleep_power_down_callback(void* unused) {
  power_on = false;
}

unlock_secret_t g_secret = {
  .bytes =
    {
      0xaa, 0xbb, 0xcc, 0xdd, 0xee, 0xff, 0x11, 0x22, 0xaa, 0xbb, 0xcc,
      0xdd, 0xee, 0xff, 0x11, 0x22, 0xaa, 0xbb, 0xcc, 0xdd, 0xee, 0xff,
      0x11, 0x22, 0xaa, 0xbb, 0xcc, 0xdd, 0xee, 0xff, 0x11, 0x22,
    },
};

static void init(void) {
  secutils_init((secutils_api_t){
    .detect_glitch = &detect_glitch,
    .secure_random = &crypto_rand_short,
    .cpu_freq = &clock_get_freq,
  });

  sleep_init(sleep_power_down_callback);
  sleep_start_power_timer();
  unlock_init_and_begin_delay();
}

static void provision_default(void) {
  uint32_t remaining_duration;
  uint32_t retry_counter;

  unlock_err_t err = unlock_check_secret(&g_secret, &remaining_duration, &retry_counter);
  cr_assert_eq(err, UNLOCK_NO_SECRET_PROVISIONED);

  bool provisioned = false;
  cr_assert_eq(unlock_secret_exists(&provisioned), UNLOCK_OK);
  cr_assert(provisioned == false);

  err = unlock_provision_secret(&g_secret);
  cr_assert_eq(err, UNLOCK_OK);
  cr_assert_eq(unlock_secret_exists(&provisioned), UNLOCK_OK);
  cr_assert(provisioned);
}

Test(unlock_test, check_provision_check, .init = init) {
  uint32_t remaining_duration;
  uint32_t retry_counter;

  provision_default();
  cr_assert_eq(authed, SECURE_FALSE);
  cr_assert_eq(unlock_check_secret(&g_secret, &remaining_duration, &retry_counter), UNLOCK_OK);
  cr_assert_eq(authed, SECURE_TRUE);
}

Test(unlock_test, rejects_invalid_secret, .init = init) {
  provision_default();

  uint32_t remaining_duration;
  uint32_t retry_counter;

  // Supply an invalid secret.
  g_secret.bytes[0] ^= 1;
  cr_assert_eq(unlock_check_secret(&g_secret, &remaining_duration, &retry_counter),
               UNLOCK_WRONG_SECRET);
}

Test(unlock_test, enforces_delay, .init = init) {
  provision_default();

  uint32_t remaining_duration;
  uint32_t retry_counter;

  // Supply an invalid secret.
  g_secret.bytes[0] ^= 1;

  for (int i = 0; i < 4; i++) {
    cr_assert_eq(unlock_check_secret(&g_secret, &remaining_duration, &retry_counter),
                 UNLOCK_WRONG_SECRET);
    cr_assert_eq(retry_counter, i + 1);
    cr_assert_eq(remaining_duration, delay_table[retry_counter]);
  }

  // Try again without waiting. Shouldn't work.
  cr_assert_eq(unlock_check_secret(&g_secret, &remaining_duration, &retry_counter),
               UNLOCK_WAITING_ON_DELAY);
  cr_assert_eq(retry_counter, 4);
  cr_assert_eq(remaining_duration, delay_table[retry_counter]);

  uint32_t time_step = 1000;
  advance_time_ms(time_step);

  cr_assert_eq(unlock_check_secret(&g_secret, &remaining_duration, &retry_counter),
               UNLOCK_WAITING_ON_DELAY);
  cr_assert_eq(retry_counter, 4);  // Shouldn't advance due to delay
  cr_assert_eq(remaining_duration, delay_table[retry_counter] - time_step);

  time_step = 9000;  // Advance time exactly equal to delay
  advance_time_ms(time_step);

  cr_assert_eq(unlock_check_secret(&g_secret, &remaining_duration, &retry_counter),
               UNLOCK_WRONG_SECRET);  // Error is wrong secret, not waiting on delay
  cr_assert_eq(retry_counter, 5);     // Should bump by one
  cr_assert_eq(remaining_duration,
               0);  // Delay period has been waited out, so remaining_duration should be 0

  // Now we'll wait longer than the delay
  time_step = delay_table[retry_counter] + 1000;
  advance_time_ms(time_step);

  cr_assert_eq(unlock_check_secret(&g_secret, &remaining_duration, &retry_counter),
               UNLOCK_WRONG_SECRET);
  cr_assert_eq(retry_counter, 6);
  cr_assert_eq(remaining_duration, 0);

  // Finish out each wrong attempt / delay period
  // Go past the attempt limit to check the delay on the default limit response
  for (int i = 0; i < (ATTEMPT_LIMIT + 10); i++) {
    uint32_t table_idx = BLK_MIN(ATTEMPT_LIMIT, retry_counter);
    time_step = delay_table[table_idx] + 1;
    advance_time_ms(time_step);

    uint32_t expected_response =
      ((retry_counter + 1) > ATTEMPT_LIMIT) ? UNLOCK_LIMIT_RESPONSE_TAKEN : UNLOCK_WRONG_SECRET;

    cr_assert_eq(unlock_check_secret(&g_secret, &remaining_duration, &retry_counter),
                 expected_response);
    cr_assert_eq(retry_counter, (i + 7));
    cr_assert_eq(remaining_duration, 0);
  }
}

Test(unlock_test, enforces_limit_response, .init = init) {
  provision_default();

  uint32_t remaining_duration;
  uint32_t retry_counter;

  unlock_secret_t wrong;
  memcpy(&wrong.bytes, &g_secret, sizeof(unlock_secret_t));

  wrong.bytes[0] ^= 1;

  for (int i = 0; i < ATTEMPT_LIMIT; i++) {
    cr_assert_eq(unlock_check_secret(&wrong, &remaining_duration, &retry_counter),
                 UNLOCK_WRONG_SECRET);
    global_time_ms += (60000 * 60);  // Advance clock by an hour
    cr_assert_eq(removed_files, false);
  }

  // The attempt which surpassed ATTEMPT_LIMIT should trigger the limit response.
  cr_assert_eq(unlock_check_secret(&wrong, &remaining_duration, &retry_counter),
               UNLOCK_LIMIT_RESPONSE_TAKEN);
  cr_assert_eq(removed_files, true);
}

Test(unlock_test, preserves_delay_period, .init = init) {
  provision_default();

  uint32_t remaining_duration;
  uint32_t retry_counter;

  // Supply an invalid secret.
  g_secret.bytes[0] ^= 1;

  // Try a few times to bump the delay period.
  for (int i = 0; i < 4; i++) {
    cr_assert_eq(unlock_check_secret(&g_secret, &remaining_duration, &retry_counter),
                 UNLOCK_WRONG_SECRET);
    cr_assert_eq(retry_counter, i + 1);
    cr_assert_eq(remaining_duration, delay_table[retry_counter]);
  }

  cr_assert_eq(unlock_check_secret(&g_secret, &remaining_duration, &retry_counter),
               UNLOCK_WAITING_ON_DELAY);
  cr_assert_eq(retry_counter, 4);
  cr_assert_eq(remaining_duration, delay_table[retry_counter]);

  cr_assert_eq(DELAY_INCOMPLETE, delay_status);

  cr_assert_eq(unlock_check_secret(&g_secret, &remaining_duration, &retry_counter),
               UNLOCK_WAITING_ON_DELAY);

  cr_assert_eq(DELAY_INCOMPLETE, delay_status);
  advance_time_ms(remaining_duration - 1);
  cr_assert_eq(DELAY_INCOMPLETE, delay_status);  // Didn't finish delay period
  advance_time_ms(1);
  cr_assert_eq(DELAY_COMPLETE, delay_status);
  advance_time_ms(1);
  cr_assert_eq(DELAY_COMPLETE, delay_status);  // Should still be complete
}
