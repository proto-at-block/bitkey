/**
 * indexfs_addr_fuzz.cc — addr_in_range() boundary arithmetic property fuzzer.
 *
 * Security finding covered:
 *   BCW-29: addr_in_range() in indexfs.c performs unsigned arithmetic to check
 *           whether an address falls within a flash region:
 *             addr >= range_start && (addr - range_start) < range_size
 *           Unsigned subtraction wraps; callers may pass boundary values
 *           (range_size == 0, addr == UINT32_MAX, range_start + range_size
 *           overflows) expecting predictable rejection.  This fuzzer verifies
 *           that the return value is consistent with the mathematical definition
 *           of range membership for all uint32_t combinations.
 *
 * Approach:
 *   src/indexfs.c is compiled directly into this target so the real
 *   addr_in_range() production function is exercised.  Hardware dependencies
 *   (fwup_*, mcu_flash_erase_page, indexfs_monotonic_*) are stubbed out; they
 *   are only reachable from erase_flash(), not from addr_in_range() itself.
 *   Under fuzz_assert.h, any invariant violation raises SIGILL, caught by
 *   libfuzzer as a crash.
 *
 *   A local reference implementation (addr_in_range_ref) encodes the
 *   mathematical specification.  I6 checks that the production function matches
 *   the reference for every (addr, range_start, range_size) triple, catching
 *   any future divergence between the spec and the implementation.
 *
 *   Invariants verified for every (addr, range_start, range_size) triple:
 *     I1: result == true  ⟹  addr >= range_start
 *     I2: result == true  ⟹  (addr - range_start) < range_size
 *     I3: result == true  ⟹  range_size > 0
 *     I4: range_size == 0 ⟹  result == false  (empty-range rejection)
 *     I5: addr < range_start ⟹  result == false
 *     I6: result == addr_in_range_ref()  (production vs. reference formula)
 */

#include "FuzzedDataProvider.h"

extern "C" {
#include "assert.h"
#include "fwup_addr.h"
#include "indexfs.h"
#include "indexfs_impl.h"      /* EXTERN_VISIBLE_FOR_TESTING → extern bool addr_in_range(...) */
#include "indexfs_monotonic.h"
#include "mcu_flash.h"
/* Must be included last to override ASSERT with __builtin_trap(). */
#include "fuzz_assert.h"
}  // extern "C"

#include <stdint.h>

/* --- Hardware and filesystem stubs ----------------------------------------
 * addr_in_range() is a pure function; these stubs satisfy the linker for the
 * other symbols compiled in from src/indexfs.c.  They are never called during
 * a well-formed fuzz run.  Pattern mirrors indexfs_test.c.
 * -------------------------------------------------------------------------- */
extern "C" {

bool rtos_in_isr(void) {
  return false;
}
void* fwup_current_slot_address(void) {
  return (void*)0x10000;
}
size_t fwup_slot_size(void) {
  return 0x2000;
}
void* fwup_bl_address(void) {
  return (void*)0x20000;
}
size_t fwup_bl_size(void) {
  return 0x2000;
}
mcu_flash_status_t mcu_flash_erase_page(uint32_t* UNUSED(address)) {
  return MCU_FLASH_STATUS_OK;
}
bool indexfs_monotonic_init(indexfs_t* UNUSED(fs)) {
  return true;
}
bool indexfs_monotonic_valid(indexfs_t* UNUSED(fs)) {
  return true;
}
uint16_t indexfs_monotonic_count(indexfs_t* UNUSED(fs)) {
  return 0;
}
bool indexfs_monotonic_increment(indexfs_t* UNUSED(fs)) {
  return true;
}
bool indexfs_monotonic_clear(indexfs_t* UNUSED(fs)) {
  return true;
}
uint8_t indexfs_monotonic_get_flag(indexfs_t* UNUSED(fs)) {
  return 0;
}
bool indexfs_monotonic_set_flag(indexfs_t* UNUSED(fs), const uint8_t UNUSED(flag)) {
  return true;
}

}  // extern "C"

/* Reference implementation: the mathematical specification of addr_in_range.
 * I6 asserts that the production function matches this for every input. */
static bool addr_in_range_ref(uint32_t addr, uint32_t range_start, uint32_t range_size) {
  return (addr >= range_start) && ((addr - range_start) < range_size);
}

static void check_invariants(uint32_t addr, uint32_t range_start, uint32_t range_size) {
  /* Call the real production function from src/indexfs.c. */
  bool result = addr_in_range(addr, range_start, range_size);

  /* I3 / I4: true result requires non-empty range. */
  if (result) {
    ASSERT(range_size > 0);
  }
  if (range_size == 0) {
    ASSERT(!result); /* I4 */
  }

  /* I1 / I5: true result requires addr >= range_start. */
  if (result) {
    ASSERT(addr >= range_start); /* I1 */
  }
  if (addr < range_start) {
    ASSERT(!result); /* I5 */
  }

  /* I2: if true, the offset must be strictly less than range_size. */
  if (result) {
    ASSERT((addr - range_start) < range_size); /* I2 */
  }

  /* I6: production function must match the reference specification. */
  ASSERT(result == addr_in_range_ref(addr, range_start, range_size)); /* I6 */
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  FuzzedDataProvider fuzzed_data(data, size);

  while (fuzzed_data.remaining_bytes() > 0) {
    uint32_t addr       = fuzzed_data.ConsumeIntegral<uint32_t>();
    uint32_t range_start = fuzzed_data.ConsumeIntegral<uint32_t>();
    uint32_t range_size  = fuzzed_data.ConsumeIntegral<uint32_t>();
    check_invariants(addr, range_start, range_size);
  }

  return 0;
}
