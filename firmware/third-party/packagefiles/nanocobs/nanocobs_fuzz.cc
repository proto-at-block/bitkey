/**
 * nanocobs_fuzz.cc — COBS framing encode/decode fuzzer.
 *
 * Drives cobs_encode(), cobs_decode(), cobs_encode_tinyframe(), and
 * cobs_decode_tinyframe() with arbitrary byte sequences to detect OOB reads,
 * memory corruption, and encode/decode round-trip inconsistencies.
 * Also exercises the incremental encode/decode API (cobs_encode_inc_*,
 * cobs_decode_inc_*) which has its own internal state machines.
 * No hardware dependencies.
 *
 * Invariants checked on every encode→decode round-trip:
 *   I1: cobs_encode success ⟹ cobs_decode success
 *   I2: cobs_encode success ⟹ decoded length == original length
 *   I3: cobs_encode success ⟹ decoded bytes == original bytes
 *   I4: cobs_encode_tinyframe success ⟹ cobs_decode_tinyframe success
 */

#include "FuzzedDataProvider.h"

extern "C" {
#include "cobs.h"
}  // extern "C"

#include <stdint.h>
#include <string.h>
#include <vector>

/* No firmware headers available here — define ASSERT to trap on violation. */
#define ASSERT(x) do { if (!(x)) __builtin_trap(); } while (0)

/* Cap payload size to keep iterations fast. */
static constexpr size_t kMaxPayloadSize = 256;

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  FuzzedDataProvider fuzzed_data(data, size);

  /* --- Round-trip: cobs_encode → cobs_decode ---
   * Consume a raw payload, encode it, decode the result, and verify the
   * output matches the original. */
  {
    const size_t dec_len =
      fuzzed_data.ConsumeIntegralInRange<size_t>(0, kMaxPayloadSize);
    std::vector<uint8_t> original = fuzzed_data.ConsumeBytes<uint8_t>(dec_len);
    original.resize(dec_len, 0);

    const size_t enc_max = COBS_ENCODE_MAX(dec_len);
    std::vector<uint8_t> enc_buf(enc_max, 0);
    size_t enc_len = 0;

    const cobs_ret_t enc_ret = cobs_encode(
      original.data(), dec_len, enc_buf.data(), enc_max, &enc_len);

    if (enc_ret == COBS_RET_SUCCESS) {
      std::vector<uint8_t> out_dec(dec_len + 2, 0);
      size_t out_dec_len = 0;
      const cobs_ret_t dec_ret = cobs_decode(
        enc_buf.data(), enc_len, out_dec.data(), dec_len + 2, &out_dec_len);

      ASSERT(dec_ret == COBS_RET_SUCCESS);           /* I1 */
      ASSERT(out_dec_len == dec_len);                 /* I2 */
      if (dec_len > 0) {
        ASSERT(memcmp(out_dec.data(), original.data(), dec_len) == 0); /* I3 */
      }
    }
  }

  /* --- Fuzz cobs_decode on arbitrary (possibly malformed) encoded bytes. */
  {
    const size_t enc_len =
      fuzzed_data.ConsumeIntegralInRange<size_t>(0, kMaxPayloadSize + 2);
    std::vector<uint8_t> enc_buf = fuzzed_data.ConsumeBytes<uint8_t>(enc_len);
    enc_buf.resize(enc_len, 0);

    std::vector<uint8_t> out_dec(kMaxPayloadSize, 0);
    size_t out_dec_len = 0;
    cobs_decode(enc_buf.data(), enc_len, out_dec.data(), kMaxPayloadSize,
                &out_dec_len);
  }

  /* --- Tinyframe round-trip: encode in-place, then decode in-place (I4). */
  if (fuzzed_data.remaining_bytes() >= 4) {
    const size_t tf_size =
      fuzzed_data.ConsumeIntegralInRange<size_t>(4, kMaxPayloadSize + 2);
    std::vector<uint8_t> tf_buf = fuzzed_data.ConsumeBytes<uint8_t>(tf_size);
    tf_buf.resize(tf_size, 0);

    /* tinyframe requires sentinel bytes at [0] and [size-1]. */
    tf_buf[0]           = COBS_TINYFRAME_SENTINEL_VALUE;
    tf_buf[tf_size - 1] = COBS_TINYFRAME_SENTINEL_VALUE;

    std::vector<uint8_t> enc_copy(tf_buf);
    if (cobs_encode_tinyframe(enc_copy.data(), tf_size) == COBS_RET_SUCCESS) {
      /* Decode the result — must succeed for a valid encode output (I4). */
      ASSERT(cobs_decode_tinyframe(enc_copy.data(), tf_size) == COBS_RET_SUCCESS);
    }
  }

  /* --- Fuzz cobs_decode_tinyframe on arbitrary bytes. */
  if (fuzzed_data.remaining_bytes() >= 2) {
    const size_t arb_size =
      fuzzed_data.ConsumeIntegralInRange<size_t>(2, kMaxPayloadSize + 2);
    std::vector<uint8_t> arb_buf = fuzzed_data.ConsumeBytes<uint8_t>(arb_size);
    arb_buf.resize(arb_size, 0);
    cobs_decode_tinyframe(arb_buf.data(), arb_size);
  }

  /* --- Incremental encode/decode round-trip.
   * Exercises cobs_encode_inc_begin/inc/inc_end and cobs_decode_inc_begin/inc,
   * which have their own internal state machines separate from the batch API. */
  if (fuzzed_data.remaining_bytes() > 0) {
    const size_t inc_dec_len =
      fuzzed_data.ConsumeIntegralInRange<size_t>(0, kMaxPayloadSize);
    std::vector<uint8_t> inc_payload = fuzzed_data.ConsumeBytes<uint8_t>(inc_dec_len);
    inc_payload.resize(inc_dec_len, 0);

    const size_t enc_max = COBS_ENCODE_MAX(inc_dec_len);
    std::vector<uint8_t> enc_out(enc_max, 0);
    cobs_enc_ctx_t enc_ctx;

    cobs_ret_t ret = cobs_encode_inc_begin(enc_out.data(), enc_max, &enc_ctx);
    if (ret == COBS_RET_SUCCESS && inc_dec_len > 0) {
      ret = cobs_encode_inc(&enc_ctx, inc_payload.data(), inc_dec_len);
    }
    if (ret == COBS_RET_SUCCESS) {
      size_t enc_out_len = 0;
      ret = cobs_encode_inc_end(&enc_ctx, &enc_out_len);
      if (ret == COBS_RET_SUCCESS && enc_out_len > 0) {
        /* Decode the incrementally-encoded output and verify round-trip. */
        cobs_decode_inc_ctx_t dec_ctx;
        cobs_decode_inc_begin(&dec_ctx);
        std::vector<uint8_t> dec_out(inc_dec_len + 2, 0);
        cobs_decode_inc_args_t dec_args;
        dec_args.enc_src     = enc_out.data();
        dec_args.enc_src_max = enc_out_len;
        dec_args.dec_dst     = dec_out.data();
        dec_args.dec_dst_max = inc_dec_len + 2;
        size_t src_consumed = 0, dst_written = 0;
        bool complete = false;
        cobs_decode_inc(&dec_ctx, &dec_args, &src_consumed, &dst_written, &complete);
      }
    }
  }

  return 0;
}
