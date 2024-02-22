#pragma once

#include "rtos.h"

#include <stdbool.h>
#include <stdint.h>

typedef struct {
  uint8_t* buf;
  uint32_t max_size;
  uint32_t head;
  uint32_t tail;
  rtos_mutex_t* lock;
  bool full;
} ringbuf_t;

bool ringbuf_push(ringbuf_t* b, uint8_t value);
bool ringbuf_pop(ringbuf_t* b, uint8_t* value);
uint32_t ringbuf_push_buf(ringbuf_t* b, uint8_t* buf, uint32_t len);
uint32_t ringbuf_pop_buf(ringbuf_t* b, uint8_t* buf, uint32_t len);

void ringbuf_advance(ringbuf_t* b, uint32_t bytes);
bool ringbuf_peek(ringbuf_t* b, uint32_t idx, uint8_t* value);

// Copy out `len` most recently added bytes into `buf`.
bool ringbuf_copy_most_recent(ringbuf_t* b, uint8_t* buf, uint32_t len);

void ringbuf_clear(ringbuf_t* b);
uint32_t ringbuf_size_contiguous(ringbuf_t* b);
bool ringbuf_empty(ringbuf_t* b);

uint32_t ringbuf_size(ringbuf_t* b);
