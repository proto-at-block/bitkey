#include "dudero.h"

#include <stdbool.h>
#include <stdint.h>

#define MIN_LEN (16)

dudero_ret_t dudero_check_buffer(const uint8_t* buf, size_t len) {
  if (len < MIN_LEN) {
    return DUDERO_RET_TOO_SHORT;
  }

  dudero_ctx_t ctx;
  dudero_stream_init(&ctx);

  for (size_t i = 0; i < len; i++) {
    dudero_stream_add(&ctx, buf[i]);
  }

  return dudero_stream_finish(&ctx);
}

dudero_ret_t dudero_stream_init(dudero_ctx_t* ctx) {
  for (size_t i = 0; i < 16; i++) {
    ctx->hist[i] = 0;
  }
  ctx->hist_samples = 0;
  return DUDERO_RET_OK;
}

dudero_ret_t dudero_stream_add(dudero_ctx_t* ctx, uint8_t sample) {
  ctx->hist[sample >> 4]++;
  ctx->hist[sample & 0x0F]++;
  ctx->hist_samples += 2;  // TODO: check this isn't larger than 2^16
  return DUDERO_RET_OK;
}

dudero_ret_t dudero_stream_finish(dudero_ctx_t* ctx) {
  // TODO: handle rounding if len isn't multiple of 8
  int expected = ctx->hist_samples / 16;
  uint32_t cum = 0;
  for (size_t i = 0; i < 16; i++) {
    uint32_t delta = (ctx->hist[i] > expected) ? ctx->hist[i] - expected : expected - ctx->hist[i];
    cum += delta * delta;
  }
  double cum_norm = (double)cum / (double)expected;
  double thres = 45.0;

  if (cum_norm > thres) {
    return DUDERO_RET_BAD_RANDOMNESS;
  }

  return DUDERO_RET_OK;
}
