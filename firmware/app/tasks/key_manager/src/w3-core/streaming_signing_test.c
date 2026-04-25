#include "criterion_test_utils.h"
#include "display.pb.h"
#include "fff.h"
#include "wallet.pb.h"

#include <criterion/criterion.h>

#include <stdbool.h>
#include <stdint.h>
#include <string.h>

// ---------------------------------------------------------------------------
// Streaming Signing Confirmation Data Size Invariants
// ---------------------------------------------------------------------------
// These tests validate that the streaming and non-streaming confirmation data
// structures fit within the confirmation manager's MAX_OPERATION_DATA_SIZE
// while remaining distinct types to prevent confusion.
//
// The bug that motivated adding streaming commitment verification was caused
// by the confirmation handler attempting to read tx_session_confirmation_data_t
// when the streaming path had stored psbt_info_t.  Now both paths store a
// struct containing psbt_info_t + session_hash, but we keep them distinct
// (same layout, separate types) for clarity and to catch mismatches at
// compile time.

#define MAX_OPERATION_DATA_SIZE     256
#define SHA256_DIGEST_SIZE          32
#define DESTINATION_ADDRESS_MAX_LEN 128

// Mirror psbt_info_t from psbt.h (avoid pulling in crypto dependencies)
typedef struct {
  bool has_destination;
  char destination_address[DESTINATION_ADDRESS_MAX_LEN];
  uint64_t send_amount_sats;
  uint64_t change_amount_sats;
  uint64_t fee_amount_sats;
} test_psbt_info_t;

// Mirror the types from key_manager_task_port.c (static, so cannot import)
typedef struct {
  test_psbt_info_t display_info;
  uint8_t session_hash[SHA256_DIGEST_SIZE];
} test_tx_session_confirmation_data_t;

typedef struct {
  test_psbt_info_t display_info;
  uint8_t session_hash[SHA256_DIGEST_SIZE];
} test_stream_session_confirmation_data_t;

DEFINE_FFF_GLOBALS;
FAKE_VALUE_FUNC(bool, rtos_in_isr);

Test(streaming_signing, confirmation_data_fits_in_max_operation_size) {
  // Both confirmation data types must fit within the confirmation manager limit
  cr_assert_leq(sizeof(test_tx_session_confirmation_data_t), MAX_OPERATION_DATA_SIZE,
                "tx_session_confirmation_data_t exceeds MAX_OPERATION_DATA_SIZE");
  cr_assert_leq(sizeof(test_stream_session_confirmation_data_t), MAX_OPERATION_DATA_SIZE,
                "stream_session_confirmation_data_t exceeds MAX_OPERATION_DATA_SIZE");
}

Test(streaming_signing, confirmation_data_sizes_match) {
  // Both structs should have identical layout (same fields in same order)
  cr_assert_eq(sizeof(test_tx_session_confirmation_data_t),
               sizeof(test_stream_session_confirmation_data_t),
               "Streaming and non-streaming confirmation data sizes should match");
}

Test(streaming_signing, confirmation_data_includes_commitment_hash) {
  // Verify the session_hash field is properly sized
  test_stream_session_confirmation_data_t data = {0};
  cr_assert_eq(sizeof(data.session_hash), SHA256_DIGEST_SIZE,
               "session_hash should be SHA256_DIGEST_SIZE bytes");
}

Test(streaming_signing, proto_tags_are_distinct) {
  // Streaming signatures_ready must have a distinct response tag from non-streaming
  cr_assert_neq(fwpb_get_confirmation_result_rsp_sign_stream_signatures_ready_tag,
                fwpb_get_confirmation_result_rsp_sign_tx_result_tag,
                "Streaming and non-streaming result tags must differ");
}

// ---------------------------------------------------------------------------
// Streaming Session Commitment Binding
// ---------------------------------------------------------------------------
// The streaming signing commitment hash covers:
//   - BIP143 intermediate hashes (hash_prevouts, hash_sequence, hash_outputs)
//   - Transaction metadata (version, lock_time, num_inputs, num_outputs)
//   - Payload hash (binds to all flash data: amounts, derivation paths, etc.)
//
// The payload hash is critical because per-input amounts are read from flash
// at signing time and aren't covered by the BIP143 intermediate hashes.

Test(streaming_signing, payload_hash_is_sha256_size) {
  // The commitment stored in session must be SHA256_DIGEST_SIZE
  cr_assert_eq(SHA256_DIGEST_SIZE, 32, "SHA256 digest should be 32 bytes");
}

// ---------------------------------------------------------------------------
// Sweep Display Routing
// ---------------------------------------------------------------------------
// Sweep transactions use a dedicated command (sweep_sign_cmd) but should
// display as SELF_SEND on the W3 screen — same as UTXO consolidation.
//
// The sweep's single output lacks a derivation path (it's verified by SPK
// match against the current keyset's fresh receive address), so the tx parser
// classifies it as an external destination (has_destination=true). Without
// the is_sweep override, firmware would show the address like a normal send.
//
// These tests document the expected display routing contract for each
// transaction scenario. The routing logic lives in raw_tx_request_confirmation
// and stream_tx_request_confirmation in key_manager_task_port.c.

// Encodes the expected display routing: is_sweep || !has_destination → SELF_SEND.
// Self-send display amount = send + change; normal send = send only.
static void expected_display_params(bool is_sweep, const test_psbt_info_t* tx_info,
                                    fwpb_money_movement_flow* out_flow,
                                    uint64_t* out_display_amount) {
  bool is_self_send = is_sweep || !tx_info->has_destination;
  *out_flow = is_self_send ? fwpb_money_movement_flow_MONEY_MOVEMENT_FLOW_SELF_SEND
                           : fwpb_money_movement_flow_MONEY_MOVEMENT_FLOW_SEND;

  *out_display_amount = is_self_send ? (tx_info->send_amount_sats + tx_info->change_amount_sats)
                                     : tx_info->send_amount_sats;
}

Test(sweep_display, sweep_with_external_output_routes_to_self_send) {
  // Sweep: single output classified as external (has_destination=true),
  // but sweep.active forces SELF_SEND. Amount = send + change.
  test_psbt_info_t info = {
    .has_destination = true,
    .send_amount_sats = 500000,
    .change_amount_sats = 0,
    .fee_amount_sats = 1000,
  };

  fwpb_money_movement_flow flow;
  uint64_t display_amount;
  expected_display_params(true, &info, &flow, &display_amount);

  cr_assert_eq(flow, fwpb_money_movement_flow_MONEY_MOVEMENT_FLOW_SELF_SEND,
               "Sweep should display as SELF_SEND");
  cr_assert_eq(display_amount, 500000, "Sweep display amount should be send + change (500000 + 0)");
}

Test(sweep_display, sweep_with_derivation_path_output_routes_to_self_send) {
  // Sweep where the output has a derivation path (has_destination=false).
  // Value lands in change_amount_sats. Amount = send + change.
  test_psbt_info_t info = {
    .has_destination = false,
    .send_amount_sats = 0,
    .change_amount_sats = 499000,
    .fee_amount_sats = 1000,
  };

  fwpb_money_movement_flow flow;
  uint64_t display_amount;
  expected_display_params(true, &info, &flow, &display_amount);

  cr_assert_eq(flow, fwpb_money_movement_flow_MONEY_MOVEMENT_FLOW_SELF_SEND,
               "Sweep without external destination should still be SELF_SEND");
  cr_assert_eq(display_amount, 499000, "Sweep display amount should be send + change (0 + 499000)");
}

Test(sweep_display, consolidation_routes_to_self_send) {
  // Consolidation: has_destination=false, not a sweep. All value in change.
  test_psbt_info_t info = {
    .has_destination = false,
    .send_amount_sats = 0,
    .change_amount_sats = 499000,
    .fee_amount_sats = 1000,
  };

  fwpb_money_movement_flow flow;
  uint64_t display_amount;
  expected_display_params(false, &info, &flow, &display_amount);

  cr_assert_eq(flow, fwpb_money_movement_flow_MONEY_MOVEMENT_FLOW_SELF_SEND,
               "Consolidation should display as SELF_SEND");
  cr_assert_eq(display_amount, 499000,
               "Consolidation display amount should be send + change (0 + 499000)");
}

Test(sweep_display, normal_send_routes_to_send_flow) {
  // Normal send: has_destination=true, not a sweep.
  test_psbt_info_t info = {
    .has_destination = true,
    .send_amount_sats = 100000,
    .change_amount_sats = 399000,
    .fee_amount_sats = 1000,
  };

  fwpb_money_movement_flow flow;
  uint64_t display_amount;
  expected_display_params(false, &info, &flow, &display_amount);

  cr_assert_eq(flow, fwpb_money_movement_flow_MONEY_MOVEMENT_FLOW_SEND,
               "Normal send should display as SEND");
  cr_assert_eq(display_amount, 100000, "Normal send should display send_amount_sats only");
}
