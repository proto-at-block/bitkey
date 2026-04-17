/**
 * sap_fuzz.cc — Sign Action Proof payload builder and action parser fuzzer.
 *
 * Drives sap_build_payload() with arbitrary sap_pending_data_t field contents
 * and sap_parse_action() with arbitrary NUL-terminated strings, detecting
 * OOB access and buffer-overrun bugs in payload construction and string
 * comparison.  Uses sap_core_dep only; no hardware dependencies.
 *
 * Invariants checked for every input:
 *   I1: sap_build_payload with a SAP_PAYLOAD_MAX_SIZE output buffer either
 *       returns -1 (invalid input) or a value in [0, SAP_PAYLOAD_MAX_SIZE]
 *   I2: sap_parse_action(known_action_string) == SAP_ACTION_<expected>
 */

#include "FuzzedDataProvider.h"

extern "C" {
#include "sign_action_proof_core.h"
/* Must be included last to override ASSERT with __builtin_trap(). */
#include "fuzz_assert.h"
}  // extern "C"

#include <stdint.h>
#include <string.h>
#include <vector>

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  FuzzedDataProvider fuzzed_data(data, size);

  /* Build a sap_pending_data_t with fuzz-controlled fields. */
  sap_pending_data_t pending = {};
  pending.valid   = fuzzed_data.ConsumeBool();
  pending.version = fuzzed_data.ConsumeIntegral<uint32_t>();

  /* Fill fixed-size string fields from fuzz bytes; always NUL-terminate. */
  {
    std::vector<uint8_t> bytes =
      fuzzed_data.ConsumeBytes<uint8_t>(sizeof(pending.action) - 1);
    memcpy(pending.action, bytes.data(), bytes.size());
    pending.action[sizeof(pending.action) - 1] = '\0';
  }
  {
    std::vector<uint8_t> bytes =
      fuzzed_data.ConsumeBytes<uint8_t>(sizeof(pending.value) - 1);
    memcpy(pending.value, bytes.data(), bytes.size());
    pending.value[sizeof(pending.value) - 1] = '\0';
  }
  {
    std::vector<uint8_t> bytes =
      fuzzed_data.ConsumeBytes<uint8_t>(sizeof(pending.bindings) - 1);
    memcpy(pending.bindings, bytes.data(), bytes.size());
    pending.bindings[sizeof(pending.bindings) - 1] = '\0';
  }

  /* I1: payload builder must not overflow the maximum output buffer. */
  uint8_t payload_buf[SAP_PAYLOAD_MAX_SIZE];
  const int payload_len =
    sap_build_payload(&pending, payload_buf, sizeof(payload_buf));
  /* I1: must either return -1 (invalid input) or a value in [0, SAP_PAYLOAD_MAX_SIZE].
   * The signed-before-unsigned check prevents a large negative error code
   * from passing the upper-bound comparison silently. */
  ASSERT(payload_len == -1 || (payload_len >= 0 && payload_len <= (int)SAP_PAYLOAD_MAX_SIZE)); /* I1 */

  /* Exercise sap_parse_action with the fuzz-generated action string. */
  (void)sap_parse_action(pending.action);

  /* I2: each known action string must parse to its expected enum value. */
  for (int i = 0; i < SAP_ACTION_COUNT; ++i) {
    ASSERT(sap_parse_action(sap_action_strings[i]) == (sap_action_t)i); /* I2 */
  }

  return 0;
}
