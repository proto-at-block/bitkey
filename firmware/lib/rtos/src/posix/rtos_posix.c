/**
 * @file rtos_posix.c
 * @brief POSIX implementations of RTOS primitives using pthreads
 *
 * Provides real queue/mutex/semaphore/thread implementations for POSIX builds,
 * allowing firmware code to run with proper concurrency semantics.
 *
 * Note: Uses pthread mutex+cond for semaphores since macOS deprecated POSIX
 * semaphores and doesn't support sem_timedwait/pthread_mutex_timedlock.
 */

#include "rtos_event_groups.h"
#include "rtos_mutex.h"
#include "rtos_queue.h"
#include "rtos_semaphore.h"
#include "rtos_thread.h"

#include <errno.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

typedef struct {
  pthread_mutex_t lock;
  pthread_cond_t not_empty;
  pthread_cond_t not_full;
  uint8_t* buffer;
  size_t item_size;
  size_t capacity;
  size_t count;
  size_t head;
  size_t tail;
} posix_queue_t;

typedef struct {
  pthread_mutex_t mtx;
  pthread_cond_t cond;
  pthread_t owner;
  bool locked;
} posix_mutex_t;

typedef struct {
  pthread_mutex_t lock;
  pthread_cond_t cond;
  int count;
} posix_semaphore_t;

typedef struct {
  pthread_t thread;
  bool valid;
} posix_thread_t;

typedef struct {
  pthread_mutex_t lock;
  pthread_cond_t cond;
  uint32_t bits;
} posix_event_group_t;

// All TIMEOUT_MAX constants are UINT32_MAX
#define IS_TIMEOUT_MAX(t) ((t) == UINT32_MAX)

static void timeout_to_abstime(uint32_t timeout_ms, struct timespec* ts) {
  clock_gettime(CLOCK_REALTIME, ts);
  ts->tv_sec += timeout_ms / 1000;
  ts->tv_nsec += (timeout_ms % 1000) * 1000000;
  if (ts->tv_nsec >= 1000000000) {
    ts->tv_sec++;
    ts->tv_nsec -= 1000000000;
  }
}

typedef enum {
  WAIT_CONTINUE,  // Condition may have changed, caller should re-check
  WAIT_TIMEOUT,   // Timeout expired without condition change
} wait_result_t;

static wait_result_t cond_wait_with_timeout(pthread_cond_t* cond, pthread_mutex_t* lock,
                                            uint32_t timeout_ms) {
  if (timeout_ms == 0) {
    return WAIT_TIMEOUT;
  }
  if (IS_TIMEOUT_MAX(timeout_ms)) {
    pthread_cond_wait(cond, lock);
    return WAIT_CONTINUE;
  }
  struct timespec ts;
  timeout_to_abstime(timeout_ms, &ts);
  if (pthread_cond_timedwait(cond, lock, &ts) != 0) {
    return WAIT_TIMEOUT;
  }
  return WAIT_CONTINUE;
}

void _rtos_queue_create_static(rtos_queue_t* queue, uint32_t item_size, uint32_t length,
                               void* buffer, StaticQueue_t* static_queue) {
  posix_queue_t* q = (posix_queue_t*)static_queue;
  pthread_mutex_init(&q->lock, NULL);
  pthread_cond_init(&q->not_empty, NULL);
  pthread_cond_init(&q->not_full, NULL);
  q->buffer = (uint8_t*)buffer;
  q->item_size = item_size;
  q->capacity = length;
  q->count = 0;
  q->head = 0;
  q->tail = 0;
  queue->handle = (QueueHandle_t)q;
}

bool rtos_queue_send(rtos_queue_t* queue, void* object, uint32_t timeout_ms) {
  posix_queue_t* q = (posix_queue_t*)queue->handle;

  pthread_mutex_lock(&q->lock);

  while (q->count >= q->capacity) {
    if (cond_wait_with_timeout(&q->not_full, &q->lock, timeout_ms) == WAIT_TIMEOUT) {
      pthread_mutex_unlock(&q->lock);
      return false;
    }
  }

  memcpy(q->buffer + (q->tail * q->item_size), object, q->item_size);
  q->tail = (q->tail + 1) % q->capacity;
  q->count++;

  pthread_cond_signal(&q->not_empty);
  pthread_mutex_unlock(&q->lock);
  return true;
}

bool rtos_queue_recv(rtos_queue_t* queue, void* object, uint32_t timeout_ms) {
  posix_queue_t* q = (posix_queue_t*)queue->handle;

  pthread_mutex_lock(&q->lock);

  while (q->count == 0) {
    if (cond_wait_with_timeout(&q->not_empty, &q->lock, timeout_ms) == WAIT_TIMEOUT) {
      pthread_mutex_unlock(&q->lock);
      return false;
    }
  }

  memcpy(object, q->buffer + (q->head * q->item_size), q->item_size);
  q->head = (q->head + 1) % q->capacity;
  q->count--;

  pthread_cond_signal(&q->not_full);
  pthread_mutex_unlock(&q->lock);
  return true;
}

// Uses condition variable for timed lock since macOS lacks pthread_mutex_timedlock
void rtos_mutex_create(rtos_mutex_t* mutex) {
  posix_mutex_t* m = (posix_mutex_t*)&mutex->buffer;
  pthread_mutex_init(&m->mtx, NULL);
  pthread_cond_init(&m->cond, NULL);
  m->locked = false;
  mutex->handle = (SemaphoreHandle_t)m;
}

void rtos_mutex_destroy(rtos_mutex_t* mutex) {
  posix_mutex_t* m = (posix_mutex_t*)mutex->handle;
  if (m) {
    pthread_mutex_destroy(&m->mtx);
    pthread_cond_destroy(&m->cond);
  }
}

bool rtos_mutex_lock(rtos_mutex_t* mutex) {
  posix_mutex_t* m = (posix_mutex_t*)mutex->handle;
  if (!m)
    return false;

  pthread_mutex_lock(&m->mtx);
  while (m->locked) {
    pthread_cond_wait(&m->cond, &m->mtx);
  }
  m->locked = true;
  m->owner = pthread_self();
  pthread_mutex_unlock(&m->mtx);
  return true;
}

bool rtos_mutex_unlock(rtos_mutex_t* mutex) {
  posix_mutex_t* m = (posix_mutex_t*)mutex->handle;
  if (!m)
    return false;

  pthread_mutex_lock(&m->mtx);
  m->locked = false;
  pthread_cond_signal(&m->cond);
  pthread_mutex_unlock(&m->mtx);
  return true;
}

bool rtos_mutex_take(rtos_mutex_t* mutex, uint32_t timeout_ms) {
  posix_mutex_t* m = (posix_mutex_t*)mutex->handle;
  if (!m)
    return false;

  if (IS_TIMEOUT_MAX(timeout_ms)) {
    return rtos_mutex_lock(mutex);
  }

  pthread_mutex_lock(&m->mtx);

  while (m->locked) {
    if (cond_wait_with_timeout(&m->cond, &m->mtx, timeout_ms) == WAIT_TIMEOUT) {
      pthread_mutex_unlock(&m->mtx);
      return false;
    }
  }

  m->locked = true;
  m->owner = pthread_self();
  pthread_mutex_unlock(&m->mtx);
  return true;
}

bool rtos_mutex_lock_from_isr(rtos_mutex_t* mutex) {
  return rtos_mutex_lock(mutex);
}

bool rtos_mutex_unlock_from_isr(rtos_mutex_t* mutex) {
  return rtos_mutex_unlock(mutex);
}

bool rtos_mutex_owner(rtos_mutex_t* mutex) {
  posix_mutex_t* m = (posix_mutex_t*)mutex->handle;
  if (!m)
    return false;
  return m->locked && pthread_equal(m->owner, pthread_self());
}

// Uses mutex+cond since macOS deprecated POSIX semaphores
void rtos_semaphore_create(rtos_semaphore_t* semaphore) {
  rtos_semaphore_create_counting(semaphore, 0, 0);
}

void rtos_semaphore_destroy(rtos_semaphore_t* semaphore) {
  posix_semaphore_t* s = (posix_semaphore_t*)semaphore->handle;
  if (s) {
    pthread_mutex_destroy(&s->lock);
    pthread_cond_destroy(&s->cond);
  }
}

void rtos_semaphore_create_counting(rtos_semaphore_t* semaphore, uint32_t max_count,
                                    uint32_t initial_count) {
  (void)max_count;
  posix_semaphore_t* s = (posix_semaphore_t*)&semaphore->buffer;
  pthread_mutex_init(&s->lock, NULL);
  pthread_cond_init(&s->cond, NULL);
  s->count = (int)initial_count;
  semaphore->handle = (SemaphoreHandle_t)s;
}

bool rtos_semaphore_take(rtos_semaphore_t* semaphore, uint32_t timeout_ms) {
  posix_semaphore_t* s = (posix_semaphore_t*)semaphore->handle;
  if (!s)
    return false;

  pthread_mutex_lock(&s->lock);

  while (s->count <= 0) {
    if (cond_wait_with_timeout(&s->cond, &s->lock, timeout_ms) == WAIT_TIMEOUT) {
      pthread_mutex_unlock(&s->lock);
      return false;
    }
  }

  s->count--;
  pthread_mutex_unlock(&s->lock);
  return true;
}

bool rtos_semaphore_take_ticks(rtos_semaphore_t* semaphore, uint32_t ticks) {
  return rtos_semaphore_take(semaphore, ticks);
}

bool rtos_semaphore_give(rtos_semaphore_t* semaphore) {
  posix_semaphore_t* s = (posix_semaphore_t*)semaphore->handle;
  if (!s)
    return false;

  pthread_mutex_lock(&s->lock);
  s->count++;
  pthread_cond_signal(&s->cond);
  pthread_mutex_unlock(&s->lock);
  return true;
}

bool rtos_semaphore_take_from_isr(rtos_semaphore_t* semaphore) {
  return rtos_semaphore_take(semaphore, 0);
}

bool rtos_semaphore_give_from_isr(rtos_semaphore_t* semaphore) {
  return rtos_semaphore_give(semaphore);
}

typedef struct {
  void (*func)(void*);
  void* args;
} thread_wrapper_args_t;

static void* thread_wrapper(void* arg) {
  thread_wrapper_args_t* wrapper = (thread_wrapper_args_t*)arg;
  void (*func)(void*) = wrapper->func;
  void* args = wrapper->args;
  free(wrapper);
  func(args);
  return NULL;
}

void rtos_thread_create_static(rtos_thread_t* thread, void (*func)(void*), const char* name,
                               void* args, rtos_thread_priority_t priority, uint32_t* stack_buffer,
                               uint32_t stack_size, StaticTask_t* task_buffer,
                               rtos_thread_mpu_t mpu_regions) {
  (void)name;
  (void)priority;
  (void)stack_buffer;
  (void)stack_size;
  (void)mpu_regions;

  posix_thread_t* t = (posix_thread_t*)task_buffer;
  thread_wrapper_args_t* wrapper = malloc(sizeof(thread_wrapper_args_t));
  wrapper->func = func;
  wrapper->args = args;

  if (pthread_create(&t->thread, NULL, thread_wrapper, wrapper) == 0) {
    t->valid = true;
    thread->handle = (uintptr_t)t;
  } else {
    free(wrapper);
    t->valid = false;
    thread->handle = 0;
  }
}

void rtos_thread_delete(rtos_thread_t* thread) {
  posix_thread_t* t = (posix_thread_t*)thread->handle;
  if (t && t->valid) {
    pthread_cancel(t->thread);
    t->valid = false;
  }
}

void rtos_thread_start_scheduler(void) {}

void rtos_thread_sleep(const uint32_t time_ms) {
  usleep(time_ms * 1000);
}

void rtos_thread_sleep_until(uint32_t* last_wake_time_ms, const uint32_t period_ms) {
  uint32_t now = rtos_thread_systime();
  uint32_t target = *last_wake_time_ms + period_ms;
  if (target > now) {
    rtos_thread_sleep(target - now);
  }
  *last_wake_time_ms = rtos_thread_systime();
}

static uint64_t start_time_us;

static uint64_t get_time_us(void) {
  struct timespec ts;
  clock_gettime(CLOCK_MONOTONIC, &ts);
  return (uint64_t)ts.tv_sec * 1000000ULL + (uint64_t)ts.tv_nsec / 1000ULL;
}

__attribute__((constructor)) static void init_start_time(void) {
  start_time_us = get_time_us();
}

uint32_t rtos_thread_systime(void) {
  return (uint32_t)((get_time_us() - start_time_us) / 1000ULL);
}

uint64_t rtos_thread_micros(void) {
  return get_time_us() - start_time_us;
}

bool rtos_in_isr(void) {
  return false;
}

/* rtos_thread_is_privileged() is provided as a no-op macro by rtos_mpu.h
 * for non-embedded builds. */

void rtos_event_group_create(rtos_event_group_t* event_group) {
  posix_event_group_t* eg = (posix_event_group_t*)&event_group->buffer;
  pthread_mutex_init(&eg->lock, NULL);
  pthread_cond_init(&eg->cond, NULL);
  eg->bits = 0;
  event_group->handle = (EventGroupHandle_t)eg;
}

void rtos_event_group_destroy(rtos_event_group_t* event_group) {
  posix_event_group_t* eg = (posix_event_group_t*)event_group->handle;
  if (eg) {
    pthread_mutex_destroy(&eg->lock);
    pthread_cond_destroy(&eg->cond);
  }
}

uint32_t rtos_event_group_set_bits(rtos_event_group_t* event_group, const uint32_t bits) {
  posix_event_group_t* eg = (posix_event_group_t*)event_group->handle;
  pthread_mutex_lock(&eg->lock);
  eg->bits |= bits;
  uint32_t result = eg->bits;
  pthread_cond_broadcast(&eg->cond);
  pthread_mutex_unlock(&eg->lock);
  return result;
}

bool rtos_event_group_set_bits_from_isr(rtos_event_group_t* event_group, const uint32_t bits,
                                        bool* higher_priority_task_woken) {
  (void)higher_priority_task_woken;
  rtos_event_group_set_bits(event_group, bits);
  return true;
}

uint32_t rtos_event_group_get_bits(rtos_event_group_t* event_group) {
  posix_event_group_t* eg = (posix_event_group_t*)event_group->handle;
  pthread_mutex_lock(&eg->lock);
  uint32_t result = eg->bits;
  pthread_mutex_unlock(&eg->lock);
  return result;
}

static bool event_bits_satisfied(uint32_t current_bits, uint32_t bits_to_wait, bool wait_all) {
  uint32_t matched = current_bits & bits_to_wait;
  if (wait_all) {
    return matched == bits_to_wait;
  }
  return matched != 0;
}

uint32_t rtos_event_group_wait_bits(rtos_event_group_t* event_group, const uint32_t bits_to_wait,
                                    const bool clear_on_exit, const bool wait_all,
                                    uint32_t timeout_ms) {
  posix_event_group_t* eg = (posix_event_group_t*)event_group->handle;

  pthread_mutex_lock(&eg->lock);

  while (!event_bits_satisfied(eg->bits, bits_to_wait, wait_all)) {
    if (cond_wait_with_timeout(&eg->cond, &eg->lock, timeout_ms) == WAIT_TIMEOUT) {
      break;
    }
  }

  uint32_t result = eg->bits;
  if (clear_on_exit && event_bits_satisfied(eg->bits, bits_to_wait, wait_all)) {
    eg->bits &= ~bits_to_wait;
  }
  pthread_mutex_unlock(&eg->lock);
  return result;
}

uint32_t rtos_event_group_clear_bits(rtos_event_group_t* event_group, const uint32_t bits) {
  posix_event_group_t* eg = (posix_event_group_t*)event_group->handle;
  pthread_mutex_lock(&eg->lock);
  uint32_t prev = eg->bits;
  eg->bits &= ~bits;
  pthread_mutex_unlock(&eg->lock);
  return prev;
}

#include "rtos_timer.h"

// Timer state stored in handle (we use handle as a pointer to our state struct)
typedef struct {
  uint64_t start_time_us;
  uint32_t duration_ms;
  bool running;
  rtos_timer_callback_t callback;
} posix_timer_state_t;

// We use static storage since handle field is meant for FreeRTOS timer handle (pointer-sized)
// and rtos_static_timer_t buffer can hold our state
static posix_timer_state_t* get_timer_state(rtos_timer_t* timer) {
  return (posix_timer_state_t*)&timer->buffer;
}

void rtos_timer_create_static(rtos_timer_t* timer, rtos_timer_callback_t callback) {
  if (timer) {
    posix_timer_state_t* state = get_timer_state(timer);
    state->start_time_us = 0;
    state->duration_ms = 0;
    state->running = false;
    state->callback = callback;
    timer->handle = NULL;
    timer->active = false;
  }
}

bool rtos_timer_expired(rtos_timer_t* timer) {
  if (!timer || !timer->active) {
    return false;
  }
  posix_timer_state_t* state = get_timer_state(timer);
  if (!state->running) {
    return false;
  }
  uint64_t now = get_time_us();
  uint64_t elapsed_us = now - state->start_time_us;
  uint64_t duration_us = (uint64_t)state->duration_ms * 1000ULL;
  return elapsed_us >= duration_us;
}

void rtos_timer_start(rtos_timer_t* timer, uint32_t duration_ms) {
  if (timer) {
    posix_timer_state_t* state = get_timer_state(timer);
    state->start_time_us = get_time_us();
    state->duration_ms = duration_ms;
    state->running = true;
    timer->active = true;
  }
}

void rtos_timer_stop(rtos_timer_t* timer) {
  if (timer) {
    posix_timer_state_t* state = get_timer_state(timer);
    state->running = false;
    timer->active = false;
  }
}

void rtos_timer_restart(rtos_timer_t* timer) {
  if (timer) {
    posix_timer_state_t* state = get_timer_state(timer);
    if (state->duration_ms > 0) {
      state->start_time_us = get_time_us();
      state->running = true;
      timer->active = true;
    }
  }
}

uint32_t rtos_timer_remaining_ms(rtos_timer_t* timer) {
  if (!timer || !timer->active) {
    return 0;
  }
  posix_timer_state_t* state = get_timer_state(timer);
  if (!state->running) {
    return 0;
  }
  uint64_t now = get_time_us();
  uint64_t elapsed_us = now - state->start_time_us;
  uint64_t duration_us = (uint64_t)state->duration_ms * 1000ULL;
  if (elapsed_us >= duration_us) {
    return 0;
  }
  return (uint32_t)((duration_us - elapsed_us) / 1000ULL);
}
