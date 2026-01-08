#include "ringbuf.h"

#include <stddef.h>
#include <stdint.h>

// These functions are not thread-safe by themselves.
static uint32_t _ringbuf_size(ringbuf_t* b);
static uint32_t ringbuf_free(ringbuf_t* b);
static void _ringbuf_push(ringbuf_t* b, uint8_t value);
static void _ringbuf_pop(ringbuf_t* b, uint8_t* value);

// Initialise the ring buffer
void ringbuf_init(ringbuf_t* b, uint8_t* buf, const uint32_t size) {
  b->buf = buf;
  b->max_size = size;
  b->head = 0;
  b->tail = 0;
  b->full = false;
}

// Push a single byte into the buffer
bool ringbuf_push(ringbuf_t* b, uint8_t value) {
  b->api.lock();
  const uint16_t next_head = (uint16_t)((b->head + 1) % b->max_size);

  bool ret = false;

  if (next_head != b->tail) {
    b->buf[b->head] = value;
    b->head = next_head;
    ret = true;
  }

  b->api.unlock();
  return ret;
}

void _ringbuf_push(ringbuf_t* b, uint8_t value) {
  const uint16_t next_head = (uint16_t)((b->head + 1) % b->max_size);
  b->buf[b->head] = value;
  b->head = next_head;
  b->full = b->head == b->tail;
}

// Push `len` bytes from `buf` into the buffer
uint32_t ringbuf_push_buf(ringbuf_t* b, uint8_t* buf, uint32_t len) {
  b->api.lock();

  uint32_t ret = 0;

  if (len > ringbuf_free(b)) {
    goto out;
  }

  for (uint32_t i = 0; i < len; i++) {
    _ringbuf_push(b, buf[i]);
    ret = i;
  }

out:
  b->api.unlock();
  return ret;
}

void _ringbuf_pop(ringbuf_t* b, uint8_t* value) {
  *value = b->buf[b->tail];
  b->tail = (b->tail + 1) % b->max_size;
  b->full = false;
}

// Return a single byte from the buffer
bool ringbuf_pop(ringbuf_t* b, uint8_t* value) {
  b->api.lock();

  bool ret = false;

  if (b->head == b->tail) {
    goto out;
  }

  *value = b->buf[b->tail];
  b->tail = (b->tail + 1) % b->max_size;
  ret = true;

out:
  b->api.unlock();
  return ret;
}

// Pop `len` bytes from the buffer into `buf`
uint32_t ringbuf_pop_buf(ringbuf_t* b, uint8_t* buf, uint32_t len) {
  b->api.lock();

  uint32_t ret = 0;

  for (uint32_t i = 0; i < len; i++) {
    _ringbuf_pop(b, &buf[i]);
    ret = i;
  }

  b->api.unlock();
  return ret;
}

// Advance the tail pointer by `bytes`
void ringbuf_advance(ringbuf_t* b, uint32_t bytes) {
  b->api.lock();
  b->tail = (b->tail + bytes) % b->max_size;
  b->api.unlock();
}

// Peek at the value at index `idx` in the buffer
bool ringbuf_peek(ringbuf_t* b, uint32_t idx, uint8_t* value) {
  b->api.lock();

  bool ret = false;
  if (idx >= _ringbuf_size(b)) {
    goto out;
  }

  *value = b->buf[(b->tail + idx) % b->max_size];
  ret = true;

out:
  b->api.unlock();
  return ret;
}

// Copy out `len` most recently added bytes into `buf`
bool ringbuf_copy_most_recent(ringbuf_t* b, uint8_t* buf, uint32_t len) {
  b->api.lock();

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
  b->api.unlock();
  return true;
}

// Clear the buffer
void ringbuf_clear(ringbuf_t* b) {
  b->api.lock();
  b->tail = b->head;
  b->full = false;
  b->api.unlock();
}

// Return the number of bytes in the buffer
uint32_t _ringbuf_size(ringbuf_t* b) {
  return ((b->head - b->tail + b->max_size) % b->max_size);
}

// Return the number of contiguous bytes in the buffer
uint32_t ringbuf_size_contiguous(ringbuf_t* b) {
  b->api.lock();
  uint32_t ret = 0;
  if (b->tail < b->head) {
    /* Return up until write pointer */
    ret = b->head - b->tail;
  } else {
    /* Return up until the end of the buffer */
    ret = b->max_size - b->tail;
  }
  b->api.unlock();
  return ret;
}

// Return the number of free bytes in the buffer
uint32_t ringbuf_free(ringbuf_t* b) {
  return b->max_size - _ringbuf_size(b);
}

// Return true if the buffer is empty
bool ringbuf_empty(ringbuf_t* b) {
  b->api.lock();
  bool ret = false;
  if (!b->full)
    ret = b->head == b->tail;
  b->api.unlock();
  return ret;
}

// Return a pointer to the tail of the buffer
uint8_t* ringbuf_tail_ptr(ringbuf_t* b) {
  b->api.lock();
  uint8_t* tail_ptr = NULL;

  if (b->head != b->tail || b->full) {  // Only provide a pointer if the buffer is not empty
    tail_ptr = &b->buf[b->tail];
  }

  b->api.unlock();
  return tail_ptr;
}

// Return the number of bytes in the buffer
uint32_t ringbuf_size(ringbuf_t* b) {
  b->api.lock();
  uint32_t size = 0;
  if (b->full) {
    size = b->max_size;
    goto out;
  }
  size = _ringbuf_size(b);
out:
  b->api.unlock();
  return size;
}
