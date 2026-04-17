/**
 * iso7816_fuzz.cc — ISO/IEC 7816-4 APDU length-field parser fuzzer.
 *
 * Security finding covered:
 *   The inline helpers lc_to_int() and le_to_int() in iso7186.h use a
 *   short/extended length discriminator: when buf[0] == 0 (extended coding),
 *   they read a uint16_t from &buf[1] via ntohs(*(uint16_t*)&buf[1]).  A
 *   caller that passes a buffer with fewer than 3 bytes and buf[0]==0 triggers
 *   a heap-buffer-overflow detectable by ASAN.
 *
 * Approach:
 *   Both functions are called with a heap-allocated buffer whose exact size is
 *   fuzz-controlled (1..4 bytes).  ASAN places a redzone after the allocation,
 *   so any access beyond buf[size-1] is caught as heap-buffer-overflow.
 *   le_to_int() is called with extended_lc=false to avoid the unsupported-
 *   extended-lc assertion at function entry; the interesting OOB path is the
 *   same regardless (driven by buf[0] == 0).
 */

#include "FuzzedDataProvider.h"

extern "C" {
/* iso7816 is header-only; include its inline functions directly. */
#include "iso7186.h"
/* Must be included last to override ASSERT with __builtin_trap(). */
#include "fuzz_assert.h"
}  // extern "C"

#include <stdint.h>
#include <vector>

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  if (size < 1) {
    return 0;
  }

  FuzzedDataProvider fuzzed_data(data, size);

  while (fuzzed_data.remaining_bytes() > 0) {
    /* Allocate exactly buf_size bytes on the heap; ASAN guards the tail. */
    const size_t buf_size =
      fuzzed_data.ConsumeIntegralInRange<size_t>(1, 4);
    std::vector<uint8_t> buf = fuzzed_data.ConsumeBytes<uint8_t>(buf_size);
    buf.resize(buf_size, 0);

    /* lc_to_int: reads buf[0]; if buf[0]==0, also reads ntohs(&buf[1]). */
    (void)lc_to_int(buf.data());

    /* le_to_int: same extended-coding path; always pass extended_lc=false
     * since the true branch hits ASSERT(!extended_lc) — unsupported. */
    (void)le_to_int(buf.data(), false);
  }

  return 0;
}
