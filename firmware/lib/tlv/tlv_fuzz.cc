/**
 * tlv_fuzz.cc — TLV (Tag-Length-Value) parser fuzzer.
 *
 * Drives tlv_init(), tlv_lookup(), tlv_add(), tlv_update(), and tlv_delete()
 * with arbitrary byte buffers.  tlv_init() validates existing TLV data in the
 * buffer and sets the internal size; the subsequent CRUD operations exercise
 * the full encoding and bounds-checking logic.  No hardware dependencies.
 *
 * Invariants checked on every iteration:
 *   I1: tlv_get_size() + tlv_get_remaining_capacity() == capacity (after init)
 *   I2: after a successful tlv_add(tag), tlv_lookup(tag) must find the entry
 *       with the correct length and byte content
 *   I3: after a successful tlv_update(tag), tlv_lookup(tag) returns the new
 *       length and byte content
 *   I4: after a successful tlv_delete(tag), tlv_lookup(tag) returns
 *       TLV_ERR_NOT_FOUND
 */

#include "FuzzedDataProvider.h"

extern "C" {
#include "tlv.h"
/* Must be included last to override ASSERT with __builtin_trap(). */
#include "fuzz_assert.h"
}  // extern "C"

#include <stdint.h>
#include <string.h>
#include <vector>

/* Cap the working buffer to keep fuzz iterations fast. */
static constexpr size_t kMaxBufSize = 512;
/* Cap per-value size to avoid O(n^2) behaviour. */
static constexpr uint16_t kMaxValueLen = 64;

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  FuzzedDataProvider fuzzed_data(data, size);

  /* Allocate a working buffer and fill its initial content from fuzz bytes.
   * tlv_init() will validate whatever bytes are present. */
  const size_t buf_size =
    fuzzed_data.ConsumeIntegralInRange<size_t>(1, kMaxBufSize);
  std::vector<uint8_t> buf = fuzzed_data.ConsumeBytes<uint8_t>(buf_size);
  buf.resize(buf_size, 0); /* zero-pad if fuzz input is exhausted */

  tlv_t tlv;
  if (tlv_init(&tlv, buf.data(), buf_size) != TLV_SUCCESS) {
    /* Corrupted buffer: re-init with empty contents. */
    for (auto& b : buf) {
      b = 0;
    }
    tlv_init(&tlv, buf.data(), buf_size);
  }

  /* I1: size + remaining == capacity after a valid init. */
  ASSERT(tlv_get_size(&tlv) + tlv_get_remaining_capacity(&tlv) == buf_size);

  /* Drive CRUD operations until fuzz input is consumed. */
  while (fuzzed_data.remaining_bytes() > 0) {
    const uint8_t op    = fuzzed_data.ConsumeIntegral<uint8_t>();
    const uint32_t tag  = fuzzed_data.ConsumeIntegral<uint32_t>();

    switch (op % 4) {
      case 0: {
        /* Lookup */
        const uint8_t* value = NULL;
        uint16_t length      = 0;
        tlv_lookup(&tlv, tag, &value, &length);
        break;
      }
      case 1: {
        /* Add — I2: if add succeeds, lookup must find the tag with correct
         * length and content. */
        const uint16_t val_len =
          fuzzed_data.ConsumeIntegralInRange<uint16_t>(0, kMaxValueLen);
        std::vector<uint8_t> val = fuzzed_data.ConsumeBytes<uint8_t>(val_len);
        val.resize(val_len, 0);

        const tlv_result_t add_res = tlv_add(&tlv, tag, val.data(), val_len);
        if (add_res == TLV_SUCCESS) {
          const uint8_t* found_val = NULL;
          uint16_t found_len       = 0;
          ASSERT(tlv_lookup(&tlv, tag, &found_val, &found_len) == TLV_SUCCESS); /* I2 */
          ASSERT(found_len == val_len);                                          /* I2 */
          if (val_len > 0) {
            ASSERT(memcmp(found_val, val.data(), val_len) == 0);                /* I2 */
          }
        }
        break;
      }
      case 2: {
        /* Update — I3: if update succeeds, lookup must return new content. */
        const uint16_t val_len =
          fuzzed_data.ConsumeIntegralInRange<uint16_t>(0, kMaxValueLen);
        std::vector<uint8_t> val = fuzzed_data.ConsumeBytes<uint8_t>(val_len);
        val.resize(val_len, 0);
        const tlv_result_t upd_res = tlv_update(&tlv, tag, val.data(), val_len);
        if (upd_res == TLV_SUCCESS) {
          const uint8_t* found_val = NULL;
          uint16_t found_len       = 0;
          ASSERT(tlv_lookup(&tlv, tag, &found_val, &found_len) == TLV_SUCCESS); /* I3 */
          ASSERT(found_len == val_len);                                          /* I3 */
          if (val_len > 0) {
            ASSERT(memcmp(found_val, val.data(), val_len) == 0);                /* I3 */
          }
        }
        break;
      }
      case 3: {
        /* Delete — I4: if delete succeeds, tag must no longer be found. */
        const tlv_result_t del_res = tlv_delete(&tlv, tag);
        if (del_res == TLV_SUCCESS) {
          const uint8_t* found_val = NULL;
          uint16_t found_len       = 0;
          ASSERT(tlv_lookup(&tlv, tag, &found_val, &found_len) != TLV_SUCCESS); /* I4 */
        }
        break;
      }
    }

    /* I1 must hold after every operation. */
    ASSERT(tlv_get_size(&tlv) + tlv_get_remaining_capacity(&tlv) == buf_size);
  }

  return 0;
}
