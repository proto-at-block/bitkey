/**
 * msgpack_fuzz.cc — MessagePack (CMP) deserializer fuzzer.
 *
 * Drives msgpack_mem_access_ro_init() + cmp_read_object() with arbitrary
 * byte buffers, exercising the full CMP object-parsing path through the
 * memory-backed reader implementation.  Uses msgpack_dep + cmp_dep; no
 * hardware dependencies.
 *
 * Approach:
 *   A read-only msgpack_mem_access_t is initialized over a fuzz-supplied
 *   buffer.  The first loop calls cmp_read_object() until it returns false
 *   (input exhausted or malformed), exercising all CMP type-dispatch branches.
 *   A second pass calls the type-specific payload-copy readers (cmp_read_str,
 *   cmp_read_bin) directly, exercising the code paths that copy variable-length
 *   payload bytes — the class of bug missed by cmp_read_object alone.
 */

#include "FuzzedDataProvider.h"

extern "C" {
#include "cmp.h"
#include "msgpack.h"
/* Must be included last to override ASSERT with __builtin_trap(). */
#include "fuzz_assert.h"
}  // extern "C"

#include <stdint.h>
#include <vector>

/* Maximum objects to read per fuzz input to bound per-iteration cost. */
static constexpr int kMaxObjects = 64;
/* Maximum payload bytes to copy when exercising str/bin readers. */
static constexpr uint32_t kMaxPayloadCopy = 64;

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  /* Allow size == 0: msgpack_mem_access_ro_init with a zero-length buffer is
   * a valid edge case and should not be short-circuited. */

  /* --- Pass 1: read objects via cmp_read_object (header-only, no payload). */
  {
    msgpack_mem_access_t mem;
    cmp_ctx_t cmp;
    msgpack_mem_access_ro_init(&cmp, &mem, data, size);

    cmp_object_t obj;
    for (int i = 0; i < kMaxObjects; ++i) {
      if (!cmp_read_object(&cmp, &obj)) {
        break;
      }
    }
  }

  /* --- Pass 2: exercise payload-copy paths via type-specific readers.
   * cmp_read_str and cmp_read_bin each read the format byte, length, and
   * payload in a single call, covering the OOB-read surface that Pass 1
   * cannot reach (Pass 1 reads only the header, not the payload bytes). */
  {
    msgpack_mem_access_t mem;
    cmp_ctx_t cmp;
    char str_buf[kMaxPayloadCopy + 1];
    uint32_t str_size = kMaxPayloadCopy;
    msgpack_mem_access_ro_init(&cmp, &mem, data, size);
    cmp_read_str(&cmp, str_buf, &str_size); /* may fail; exercises the read path */
  }
  {
    msgpack_mem_access_t mem;
    cmp_ctx_t cmp;
    uint8_t bin_buf[kMaxPayloadCopy];
    uint32_t bin_size = kMaxPayloadCopy;
    msgpack_mem_access_ro_init(&cmp, &mem, data, size);
    cmp_read_bin(&cmp, bin_buf, &bin_size); /* may fail; exercises the read path */
  }

  return 0;
}
