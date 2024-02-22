#include "bitlog.h"

#include "assert.h"
#include "bitlog_impl.h"
#include "bitops.h"
#include "hex.h"
#include "log.h"
#include "perf.h"

STATIC_VISIBLE_FOR_TESTING uint8_t SHARED_TASK_BSS bitlog_storage[BITLOG_STORAGE_SIZE] = {0};

STATIC_VISIBLE_FOR_TESTING bitlog_priv_t SHARED_TASK_BSS bitlog_priv = {
  .timestamp_cb = NULL,
  .prev_timestamp = 0,
};

static uint32_t bitlog_timestamp(void) {
  const uint32_t now = bitlog_priv.timestamp_cb();
  const uint32_t delta = now - bitlog_priv.prev_timestamp;
  bitlog_priv.prev_timestamp = now;
  return delta;
}

static inline uint24_t _truncate_24(void* reg) {
#if INTPTR_MAX == INT32_MAX
  ASSERT((uintptr_t)reg <= UINT32_MAX);
#endif
  uint24_t out = UINT24((uintptr_t)reg & 0x00FFFFFF);
  return out;
}

void bitlog_init(bitlog_api_t api) {
  bitlog_priv.timestamp_cb = api.timestamp_cb;
  bitlog_priv.perf.dropped_events = perf_create(PERF_COUNT, bitlog_dropped_events);

  rtos_mutex_create(&bitlog_priv.ringbuf_lock);

  bitlog_priv.ringbuf.buf = bitlog_storage;
  bitlog_priv.ringbuf.max_size = BITLOG_STORAGE_SIZE;
  bitlog_priv.ringbuf.head = 0;
  bitlog_priv.ringbuf.tail = 0;
  bitlog_priv.ringbuf.lock = &bitlog_priv.ringbuf_lock;
  bitlog_priv.ringbuf.full = false;
}

void _bitlog_record_event(uint16_t event, uint8_t status, void* pc, void* lr) {
  const bitlog_event_t bitlog_event = {
    .timestamp_delta = bitlog_timestamp(),
    .event = event,
    .status = status,
    .pc = _truncate_24(pc),
    .lr = _truncate_24(lr),
  };

  ringbuf_push_buf(&bitlog_priv.ringbuf, (uint8_t*)&bitlog_event, sizeof(bitlog_event));
}

uint32_t bitlog_drain(uint8_t* buffer, uint32_t buffer_length, uint32_t* bytes_written) {
  ASSERT(buffer_length >= sizeof(bitlog_event_t));

  uint32_t offset = 0;
  while (!ringbuf_empty(&bitlog_priv.ringbuf) && (offset < buffer_length)) {
    ringbuf_pop_buf(&bitlog_priv.ringbuf, &buffer[offset], sizeof(bitlog_event_t));
    offset += sizeof(bitlog_event_t);
  }

  *bytes_written = offset;

  return ringbuf_size(&bitlog_priv.ringbuf);
}

bool bitlog_most_recent_event(bitlog_event_t* event_out) {
  return ringbuf_copy_most_recent(&bitlog_priv.ringbuf, (uint8_t*)event_out,
                                  sizeof(bitlog_event_t));
}

void bitlog_print(bitlog_event_t* event) {
  LOGD("bitlog:");
  LOGD("  delta  : %hu", event->timestamp_delta);
  LOGD("  event  : 0x%x", event->event);
  LOGD("  status : 0x%x", event->status);
  LOGD("  pc     : 0x%x", event->pc.v);
  LOGD("  lr     : 0x%x", event->lr.v);
}
