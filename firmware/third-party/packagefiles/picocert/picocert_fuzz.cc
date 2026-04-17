/**
 * picocert_fuzz.cc — picocert certificate chain validation fuzzer.
 *
 * Drives picocert_validate_cert_chain() and picocert_validate_cert() with
 * arbitrary byte arrays, exercising certificate field parsing, issuer/subject
 * name checks, validity-period logic, and chain-walking without making real
 * cryptographic calls.  picocert is header-only; no link-time hardware
 * dependencies.
 *
 * Crypto callbacks:
 *   hash_fn       — always succeeds, returns a zero digest (structural paths run)
 *   ecc_verify_fn — fuzz-controlled: true half the time so post-signature code
 *                   paths (issuer-name match, validity-period checks) are reached
 *   time_fn       — fuzz-controlled uint64 so both valid and expired branches run
 */

#include "FuzzedDataProvider.h"

extern "C" {
/* picocert is header-only; all functions are static inline. */
#include "picocert.h"
}  // extern "C"

#include <stdint.h>
#include <string.h>
#include <vector>

/* --- Per-iteration fuzz-controlled state ---------------------------------- */

/* Set from fuzz data at the start of each iteration.  Allows the always-false
 * stub to be flipped so that the post-signature code paths in
 * picocert_validate_cert (issuer-name match, validity-period checks) are
 * reachable by the fuzzer. */
static bool g_ecc_verify_result = false;

/* --- Stub crypto callbacks ------------------------------------------------ */

static bool stub_hash(const uint8_t* /* data */, uint32_t /* data_len */,
                      uint8_t* digest, uint32_t digest_len) {
  if (digest && digest_len > 0) {
    memset(digest, 0, digest_len);
  }
  return true;
}

static bool stub_ecc_verify(const uint8_t* /* key */, size_t /* key_size */,
                             const uint8_t* /* hash */, uint32_t /* hash_len */,
                             const uint8_t* /* signature */) {
  return g_ecc_verify_result;
}

static uint64_t stub_time(void) {
  return 0; /* Epoch; exercises validity-period checks when valid_from > 0. */
}

/* -------------------------------------------------------------------------- */

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  FuzzedDataProvider fuzzed_data(data, size);

  /* Control whether signature verification "succeeds" this iteration.
   * When true, the post-signature checks (issuer name, expiry) are reachable,
   * exercising strncmp and integer-comparison code paths. */
  g_ecc_verify_result = fuzzed_data.ConsumeBool();

  picocert_context_t ctx;
  picocert_init_context(&ctx, stub_hash, stub_ecc_verify, stub_time);

  /* Build a chain of 1..PICOCERT_MAX_CHAIN_LEN certificates from fuzz bytes. */
  const size_t chain_len =
    fuzzed_data.ConsumeIntegralInRange<size_t>(1, PICOCERT_MAX_CHAIN_LEN);
  std::vector<picocert_t> chain(chain_len);

  for (size_t i = 0; i < chain_len; ++i) {
    std::vector<uint8_t> cert_bytes =
      fuzzed_data.ConsumeBytes<uint8_t>(sizeof(picocert_t));
    cert_bytes.resize(sizeof(picocert_t), 0);
    memcpy(&chain[i], cert_bytes.data(), sizeof(picocert_t));
  }

  /* Validate the full chain — exercises chain-walking and issuer checks. */
  (void)picocert_validate_cert_chain(&ctx, chain.data(), chain_len);

  /* Validate a single certificate pair.
   * Per picocert_validate_cert_chain's convention (picocert.h lines 444-448),
   * chain[i] is the subject and chain[i+1] is the issuer.  The explicit call
   * here passes chain[0] as subject and chain[1] as issuer (matching that
   * convention) to exercise the single-pair validation path. */
  if (chain_len >= 2) {
    (void)picocert_validate_cert(&ctx, &chain[1] /* issuer */, &chain[0] /* subject */);
  }

  /* Self-signed check: same certificate as both issuer and subject. */
  (void)picocert_validate_cert(&ctx, &chain[0], &chain[0]);

  return 0;
}
