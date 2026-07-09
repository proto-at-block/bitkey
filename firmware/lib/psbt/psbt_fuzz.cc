/**
 * psbt_fuzz.cc — Bitcoin PSBT (Partially Signed Bitcoin Transaction) parser fuzzer.
 *
 * Drives psbt_get_info() and psbt_get_info_from_tx_fields() with arbitrary
 * byte buffers, exercising PSBT binary parsing, transaction shape validation,
 * address encoding, and fee arithmetic.  Mirrors the psbt_test build:
 * src/psbt.c compiled directly with posix crypto + libwally deps.
 *
 * psbt_lib_init() is called once at startup to set up libwally's memory pool.
 * Each fuzz iteration exercises:
 *   - psbt_get_info() with raw fuzz bytes (exercises the BIP174 binary parser)
 *   - psbt_compact_sig_to_der() with a 64-byte fuzz signature
 */

#include "FuzzedDataProvider.h"

extern "C" {
#include "attributes.h"
#include "fff.h"
#include "mempool.h"
#include "psbt.h"
#include "rtos.h"

/* Stubs for RTOS mutex primitives used by mempool (called from psbt_lib_init). */
FAKE_VOID_FUNC(rtos_mutex_create, rtos_mutex_t*);
FAKE_VALUE_FUNC(bool, rtos_mutex_lock, rtos_mutex_t*);
FAKE_VALUE_FUNC(bool, rtos_mutex_unlock, rtos_mutex_t*);

/* Must be included last to override ASSERT with __builtin_trap(). */
#include "fuzz_assert.h"
}  // extern "C"

DEFINE_FFF_GLOBALS;

#include <stdint.h>
#include <stdlib.h>
#include <vector>

static constexpr size_t kMaxFuzzPsbtSize = 2048;
/* TODO(W-16140): Keep this in sync when WALLY_MEMPOOL_REGIONS is resized for
 * multi-input PSBT support. Mirrors the largest current region in src/psbt.c. */
static constexpr uint32_t kMaxFuzzMempoolAllocSize = 896;

/* psbt.c is compiled for this fuzz target with mempool_alloc/mempool_free
 * renamed to these harness functions. Use malloc/free so the fuzzer is not
 * bounded by fixed pool capacity, but until W-16140 resizes the production
 * regions, treat oversized libwally allocations as parse failures. For
 * supported sizes, preserve production's non-null-or-fatal contract. */
extern "C" {
void* psbt_fuzz_mempool_alloc(mempool_t* pool, uint32_t size) {
  (void)pool;
  if (size == 0 || size > kMaxFuzzMempoolAllocSize) return NULL;

  void* buffer = malloc(size);
  ASSERT(buffer != NULL);
  return buffer;
}

void psbt_fuzz_mempool_free(mempool_t* pool, void* buffer) {
  (void)pool;
  free(buffer);
}
}

/* psbt_lib_init() is idempotent after first call; run once at process startup. */
static const bool kLibInit = []() -> bool {
  return psbt_lib_init();
}();

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  (void)kLibInit;

  if (size == 0) {
    return 0;
  }

  FuzzedDataProvider fuzzed_data(data, size);

  /* --- Fuzz psbt_get_info with arbitrary PSBT bytes. ---
   * The PSBT binary format starts with a magic header (0x70736274ff);
   * libFuzzer will discover valid and malformed cases automatically. */
  {
    /* Production firmware rejects PSBTs > MAX_PSBT_SIZE (1536 bytes).
     * Keep the fuzz parser input slightly higher to preserve edge-case coverage
     * while avoiding false timeouts in libwally's BIP174 parser. */
    const size_t max_psbt_len =
      size < kMaxFuzzPsbtSize ? size : kMaxFuzzPsbtSize;
    const size_t psbt_len =
      fuzzed_data.ConsumeIntegralInRange<size_t>(0, max_psbt_len);
    std::vector<uint8_t> psbt_bytes =
      fuzzed_data.ConsumeBytes<uint8_t>(psbt_len);
    psbt_bytes.resize(psbt_len, 0);

    /* Try all three network types to exercise address-encoding paths. */
    psbt_info_t info;
    for (int net = 0; net < 3; ++net) {
      (void)psbt_get_info(psbt_bytes.data(), psbt_len,
                          static_cast<ew_network_t>(net), &info);
    }
  }

  /* --- Fuzz psbt_compact_sig_to_der with arbitrary compact signatures. */
  if (fuzzed_data.remaining_bytes() >= 64) {
    std::vector<uint8_t> sig = fuzzed_data.ConsumeBytes<uint8_t>(64);
    uint8_t der_buf[PSBT_SIGNATURE_MAX_LEN + 8]; /* headroom above documented max */
    size_t der_len = 0;
    const psbt_error_t sig_ret =
      psbt_compact_sig_to_der(sig.data(), sig.size(), der_buf,
                              sizeof(der_buf), &der_len);
    if (sig_ret == PSBT_OK) {
      ASSERT(der_len <= PSBT_DER_SIGNATURE_MAX_LEN);
    }
  }

  /* --- Fuzz psbt_get_info_from_tx_fields with arbitrary input/output arrays.
   * This function computes fee arithmetic directly from caller-supplied amounts
   * without going through the BIP174 binary parser — a distinct code path that
   * exercises integer arithmetic and address-encoding logic. */
  {
    const size_t input_count =
      fuzzed_data.ConsumeIntegralInRange<size_t>(0, 8);
    const size_t output_count =
      fuzzed_data.ConsumeIntegralInRange<size_t>(0, 8);

    std::vector<psbt_tx_input_info_t> inputs(input_count);
    for (size_t i = 0; i < input_count; ++i) {
      inputs[i].amount_sats = fuzzed_data.ConsumeIntegral<uint64_t>();
    }

    /* script_pubkey buffers must remain valid for the call duration. */
    std::vector<std::vector<uint8_t>> spk_bufs(output_count);
    std::vector<psbt_tx_output_info_t> outputs(output_count);
    for (size_t i = 0; i < output_count; ++i) {
      outputs[i].amount_sats     = fuzzed_data.ConsumeIntegral<uint64_t>();
      outputs[i].has_keypath     = fuzzed_data.ConsumeBool();
      const size_t spk_len =
        fuzzed_data.ConsumeIntegralInRange<size_t>(0, 34);
      spk_bufs[i] = fuzzed_data.ConsumeBytes<uint8_t>(spk_len);
      spk_bufs[i].resize(spk_len, 0);
      outputs[i].script_pubkey     = spk_bufs[i].data();
      outputs[i].script_pubkey_len = spk_len;
    }

    psbt_info_t tx_info;
    for (int net = 0; net < 3; ++net) {
      (void)psbt_get_info_from_tx_fields(
        inputs.data(), input_count,
        outputs.data(), output_count,
        static_cast<ew_network_t>(net), &tx_info);
    }
  }

  return 0;
}
