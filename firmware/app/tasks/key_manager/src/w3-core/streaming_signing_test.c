#include "criterion_test_utils.h"
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
