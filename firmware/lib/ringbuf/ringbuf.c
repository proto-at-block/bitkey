#include "ringbuf.h"

// These functions are not thread-safe by themselves.
static uint32_t _ringbuf_size(ringbuf_t* b);
static uint32_t ringbuf_free(ringbuf_t* b);
static void _ringbuf_push(ringbuf_t* b, uint8_t value);
static void _ringbuf_pop(ringbuf_t* b, uint8_t* value);

bool ringbuf_push(ringbuf_t* b, uint8_t value) {
  rtos_mutex_lock(b->lock);
  const uint16_t next_head = (b->head + 1) % b->max_size;

  bool ret = false;

  if (next_head != b->tail) {
    b->buf[b->head] = value;
    b->head = next_head;
    ret = true;
  }

  rtos_mutex_unlock(b->lock);
  return ret;
}

void _ringbuf_push(ringbuf_t* b, uint8_t value) {
  const uint16_t next_head = (b->head + 1) % b->max_size;
  b->buf[b->head] = value;
  b->head = next_head;
  b->full = b->head == b->tail;
}

uint32_t ringbuf_push_buf(ringbuf_t* b, uint8_t* buf, uint32_t len) {
  rtos_mutex_lock(b->lock);

  uint32_t ret = 0;

  if (len > ringbuf_free(b)) {
    goto out;
  }

  for (uint32_t i = 0; i < len; i++) {
    _ringbuf_push(b, buf[i]);
    ret = i;
  }

out:
  rtos_mutex_unlock(b->lock);
  return ret;
}

void _ringbuf_pop(ringbuf_t* b, uint8_t* value) {
  *value = b->buf[b->tail];
  b->tail = (b->tail + 1) % b->max_size;
  b->full = false;
}

bool ringbuf_pop(ringbuf_t* b, uint8_t* value) {
  rtos_mutex_lock(b->lock);

  bool ret = false;

  if (b->head == b->tail) {
    goto out;
  }

  *value = b->buf[b->tail];
  b->tail = (b->tail + 1) % b->max_size;
  ret = true;

out:
  rtos_mutex_unlock(b->lock);
  return ret;
}

uint32_t ringbuf_pop_buf(ringbuf_t* b, uint8_t* buf, uint32_t len) {
  rtos_mutex_lock(b->lock);

  uint32_t ret = 0;

  for (uint32_t i = 0; i < len; i++) {
    _ringbuf_pop(b, &buf[i]);
    ret = i;
  }

  rtos_mutex_unlock(b->lock);
  return ret;
}

void ringbuf_advance(ringbuf_t* b, uint32_t bytes) {
  rtos_mutex_lock(b->lock);
  b->tail = (b->tail + bytes) % b->max_size;
  rtos_mutex_unlock(b->lock);
}

bool ringbuf_peek(ringbuf_t* b, uint32_t idx, uint8_t* value) {
  rtos_mutex_lock(b->lock);

  bool ret = false;
  if (idx >= _ringbuf_size(b)) {
    goto out;
  }

  *value = b->buf[(b->tail + idx) % b->max_size];
  ret = true;

out:
  rtos_mutex_unlock(b->lock);
  return ret;
}

bool ringbuf_copy_most_recent(ringbuf_t* b, uint8_t* buf, uint32_t len) {
  rtos_mutex_lock(b->lock);

  if (b->max_size < len) {
    goto out;
  }

  int diff = ((int32_t)b->head - (int32_t)len);
  uint32_t ptr = (diff < 0) ? b->max_size - len : (uint32_t)diff;

  for (uint32_t i = 0; i < len; i++) {
    buf[i] = b->buf[ptr];
    ptr = (ptr + 1) % b->max_size;
  }

out:
  rtos_mutex_unlock(b->lock);
  return true;
}

void ringbuf_clear(ringbuf_t* b) {
  rtos_mutex_lock(b->lock);
  b->tail = b->head;
  b->full = false;
  rtos_mutex_unlock(b->lock);
}

uint32_t _ringbuf_size(ringbuf_t* b) {
  return ((b->head - b->tail + b->max_size) % b->max_size);
}

uint32_t ringbuf_size_contiguous(ringbuf_t* b) {
  rtos_mutex_lock(b->lock);
  uint32_t ret = 0;
  if (b->tail < b->head) {
    /* Return up until write pointer */
    ret = b->head - b->tail;
  } else {
    /* Return up until the end of the buffer */
    ret = b->max_size - b->tail;
  }
  rtos_mutex_unlock(b->lock);
  return ret;
}

uint32_t ringbuf_free(ringbuf_t* b) {
  return b->max_size - _ringbuf_size(b);
}

bool ringbuf_empty(ringbuf_t* b) {
  rtos_mutex_lock(b->lock);
  bool ret = false;
  if (!b->full)
    ret = b->head == b->tail;
  rtos_mutex_unlock(b->lock);
  return ret;
}

uint32_t ringbuf_size(ringbuf_t* b) {
  rtos_mutex_lock(b->lock);
  uint32_t size = 0;
  if (b->full) {
    size = b->max_size;
    goto out;
  }
  size = _ringbuf_size(b);
out:
  rtos_mutex_unlock(b->lock);
  return size;
}
