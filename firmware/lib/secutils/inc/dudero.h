#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

typedef enum {
  DUDERO_RET_OK = 0,
  DUDERO_RET_ERROR,  // generic error
  DUDERO_RET_BAD_RANDOMNESS,
  DUDERO_RET_TOO_SHORT,  // passed buffer is too short
  DUDERO_RET_KNOWN_BAD,
} dudero_ret_t;

typedef struct {
  uint16_t hist[16];  // count up to 2^16 = 65 536
  size_t hist_samples;
} dudero_ctx_t;

// Checks if the passed buffer "looks random".  Fails if the passed
// buffer looks like "bad randomness" (obviously biased values, fixed values, etc).
//
// What to do when this test fails? There's a chance a perfect
// entropy source generates sequences that fail this test. This
// happens with chance ...
//
// WARNING: rejecting sequences that fail this test will reduce the source entropy!
//
dudero_ret_t dudero_check_buffer(const uint8_t* buf, size_t len);

// you need to use either the buffer OR the stream API,
// mixing them is bad
//
dudero_ret_t dudero_stream_init(dudero_ctx_t* ctx);
dudero_ret_t dudero_stream_add(dudero_ctx_t* ctx, uint8_t sample);
dudero_ret_t dudero_stream_finish(dudero_ctx_t* ctx);
