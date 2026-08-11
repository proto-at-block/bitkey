/**
 * @file rtos_posix_test.c
 * @brief Unit tests for the pthread-based POSIX RTOS shim (rtos_posix.c).
 *
 * These pin the API contracts that firmware tasks rely on when running in
 * core-sim: queue ordering and blocking behavior, mutex ownership,
 * counting semaphores, event groups (including cross-thread wakeup),
 * thread creation and time, and the polling timer semantics.
 *
 * Timing assertions use generous bounds so the suite stays reliable on
 * loaded CI machines.
 */

#include "rtos.h"

#include <criterion/criterion.h>

#include <stdbool.h>
#include <stdint.h>

// ---------------------------------------------------------------------------
// Queues
// ---------------------------------------------------------------------------

Test(rtos_posix_queue, send_recv_fifo) {
  rtos_queue_t* q = rtos_queue_create(fifo_q, uint32_t, 4);

  for (uint32_t i = 1; i <= 3; i++) {
    cr_assert(rtos_queue_send(q, &i, 0));
  }
  for (uint32_t i = 1; i <= 3; i++) {
    uint32_t out = 0;
    cr_assert(rtos_queue_recv(q, &out, 0));
    cr_assert_eq(out, i);
  }
}

Test(rtos_posix_queue, recv_timeout_on_empty) {
  rtos_queue_t* q = rtos_queue_create(timeout_q, uint32_t, 2);

  const uint32_t start = rtos_thread_systime();
  uint32_t out = 0;
  cr_assert_not(rtos_queue_recv(q, &out, 50));
  cr_assert_geq(rtos_thread_systime() - start, 40u);
}

static rtos_queue_t* g_wakeup_q;

static void queue_sender_thread(void* arg) {
  (void)arg;
  rtos_thread_sleep(100);
  uint32_t value = 0xfeed;
  rtos_queue_send(g_wakeup_q, &value, 0);
  for (;;) {
    rtos_thread_sleep(1000);
  }
}

Test(rtos_posix_queue, blocking_recv_woken_by_sender, .timeout = 10) {
  g_wakeup_q = rtos_queue_create(wakeup_q, uint32_t, 2);
  rtos_thread_create(queue_sender_thread, NULL, RTOS_THREAD_PRIORITY_NORMAL, 16384);

  uint32_t out = 0;
  cr_assert(rtos_queue_recv(g_wakeup_q, &out, 5000));
  cr_assert_eq(out, 0xfeedu);
}

// ---------------------------------------------------------------------------
// Mutexes
// ---------------------------------------------------------------------------

Test(rtos_posix_mutex, lock_unlock_ownership) {
  rtos_mutex_t mutex = {0};
  rtos_mutex_create(&mutex);

  cr_assert_not(rtos_mutex_owner(&mutex));
  cr_assert(rtos_mutex_lock(&mutex));
  cr_assert(rtos_mutex_owner(&mutex));
  cr_assert(rtos_mutex_unlock(&mutex));
  cr_assert_not(rtos_mutex_owner(&mutex));
}

typedef struct {
  rtos_mutex_t* mutex;
  rtos_semaphore_t* done;
  bool take_result;
} mutex_contention_ctx_t;

static void mutex_contender_thread(void* arg) {
  mutex_contention_ctx_t* ctx = arg;
  ctx->take_result = rtos_mutex_take(ctx->mutex, 100);
  rtos_semaphore_give(ctx->done);
  for (;;) {
    rtos_thread_sleep(1000);
  }
}

Test(rtos_posix_mutex, take_times_out_when_held_elsewhere, .timeout = 10) {
  static rtos_mutex_t mutex = {0};
  static rtos_semaphore_t done = {0};
  rtos_mutex_create(&mutex);
  rtos_semaphore_create(&done);

  cr_assert(rtos_mutex_lock(&mutex));

  static mutex_contention_ctx_t ctx;
  ctx.mutex = &mutex;
  ctx.done = &done;
  ctx.take_result = true;
  rtos_thread_create(mutex_contender_thread, &ctx, RTOS_THREAD_PRIORITY_NORMAL, 16384);

  cr_assert(rtos_semaphore_take(&done, 5000));
  cr_assert_not(ctx.take_result, "take() in another thread must time out while we hold the lock");

  cr_assert(rtos_mutex_unlock(&mutex));
}

// ---------------------------------------------------------------------------
// Semaphores
// ---------------------------------------------------------------------------

Test(rtos_posix_semaphore, counting_semantics) {
  rtos_semaphore_t sem = {0};
  rtos_semaphore_create_counting(&sem, 2, 0);

  cr_assert_not(rtos_semaphore_take(&sem, 0));

  cr_assert(rtos_semaphore_give(&sem));
  cr_assert(rtos_semaphore_give(&sem));

  cr_assert(rtos_semaphore_take(&sem, 0));
  cr_assert(rtos_semaphore_take(&sem, 0));
  cr_assert_not(rtos_semaphore_take(&sem, 0));
}

// ---------------------------------------------------------------------------
// Event groups
// ---------------------------------------------------------------------------

Test(rtos_posix_event_group, set_get_clear) {
  rtos_event_group_t group = {0};
  rtos_event_group_create(&group);

  rtos_event_group_set_bits(&group, 0x5);
  cr_assert_eq(rtos_event_group_get_bits(&group), 0x5u);

  rtos_event_group_clear_bits(&group, 0x1);
  cr_assert_eq(rtos_event_group_get_bits(&group), 0x4u);
}

Test(rtos_posix_event_group, wait_returns_immediately_when_set) {
  rtos_event_group_t group = {0};
  rtos_event_group_create(&group);
  rtos_event_group_set_bits(&group, 0x2);

  const uint32_t bits =
    rtos_event_group_wait_bits(&group, 0x2, false /* clear */, true /* wait_all */, 1000);
  cr_assert(bits & 0x2);
}

static rtos_event_group_t g_wakeup_group;

static void event_setter_thread(void* arg) {
  (void)arg;
  rtos_thread_sleep(100);
  rtos_event_group_set_bits(&g_wakeup_group, 0x8);
  for (;;) {
    rtos_thread_sleep(1000);
  }
}

Test(rtos_posix_event_group, waiter_woken_by_other_thread, .timeout = 10) {
  rtos_event_group_create(&g_wakeup_group);
  rtos_thread_create(event_setter_thread, NULL, RTOS_THREAD_PRIORITY_NORMAL, 16384);

  const uint32_t bits =
    rtos_event_group_wait_bits(&g_wakeup_group, 0x8, true /* clear */, true /* wait_all */, 5000);
  cr_assert(bits & 0x8);
}

// ---------------------------------------------------------------------------
// Threads and time
// ---------------------------------------------------------------------------

static rtos_semaphore_t g_thread_ran;

static void flag_setter_thread(void* arg) {
  (void)arg;
  rtos_semaphore_give(&g_thread_ran);
  for (;;) {
    rtos_thread_sleep(1000);
  }
}

Test(rtos_posix_thread, created_thread_runs, .timeout = 10) {
  rtos_semaphore_create(&g_thread_ran);
  rtos_thread_create(flag_setter_thread, NULL, RTOS_THREAD_PRIORITY_HIGH, 16384);
  cr_assert(rtos_semaphore_take(&g_thread_ran, 5000));
}

Test(rtos_posix_thread, sleep_and_systime_monotonic) {
  const uint32_t start_ms = rtos_thread_systime();
  const uint64_t start_us = rtos_thread_micros();

  rtos_thread_sleep(50);

  cr_assert_geq(rtos_thread_systime() - start_ms, 40u);
  cr_assert_geq(rtos_thread_micros() - start_us, 40000u);
  cr_assert_not(rtos_in_isr());
}

// ---------------------------------------------------------------------------
// Timers (polling semantics — the shim never fires callbacks; see README.md)
// ---------------------------------------------------------------------------

static void unused_timer_callback(rtos_timer_handle_t handle) {
  (void)handle;
}

Test(rtos_posix_timer, start_expire_stop_restart) {
  static rtos_timer_t timer = {0};
  rtos_timer_create_static(&timer, unused_timer_callback);

  cr_assert_not(rtos_timer_expired(&timer));

  rtos_timer_start(&timer, 50);
  cr_assert_not(rtos_timer_expired(&timer));
  cr_assert_leq(rtos_timer_remaining_ms(&timer), 50u);

  rtos_thread_sleep(80);
  cr_assert(rtos_timer_expired(&timer));
  cr_assert_eq(rtos_timer_remaining_ms(&timer), 0u);

  rtos_timer_stop(&timer);
  cr_assert_not(rtos_timer_expired(&timer));

  rtos_timer_restart(&timer);
  cr_assert_not(rtos_timer_expired(&timer));
  rtos_thread_sleep(80);
  cr_assert(rtos_timer_expired(&timer));
}
