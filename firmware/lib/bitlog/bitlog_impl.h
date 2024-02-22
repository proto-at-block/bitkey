#pragma once

#include "bitlog.h"
#include "perf.h"
#include "ringbuf.h"

#define BITLOG_MAX_EVENTS   512
#define BITLOG_STORAGE_SIZE (BITLOG_MAX_EVENTS * sizeof(bitlog_event_t))

typedef struct {
  bitlog_timestamp_t timestamp_cb;
  uint32_t prev_timestamp;
  ringbuf_t ringbuf;
  rtos_mutex_t ringbuf_lock;
  struct {
    perf_counter_t* dropped_events;
  } perf;
} bitlog_priv_t;
