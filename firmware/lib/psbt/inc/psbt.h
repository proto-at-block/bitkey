#pragma once

#include "bip32.h"
#include "ecc.h"
#include "ew.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define DESTINATION_ADDRESS_MAX_LEN 91  // 90 chars + null terminator

typedef enum {
  PSBT_OK = 0,
  PSBT_ERROR_INVALID_PARAM,
  PSBT_ERROR_PARSE_FAILED,
  PSBT_ERROR_MISSING_UTXO,
  PSBT_ERROR_INVALID_SHAPE,
  PSBT_ERROR_ADDRESS_FAILED,
  PSBT_ERROR_INVALID_KEYPATH,
  PSBT_ERROR_SCRIPT_MISMATCH,
  PSBT_ERROR_SIGHASH_FAILED,
} psbt_error_t;

typedef struct {
  bool has_destination;  // false for sweep-to-self (change only)
  char destination_address[DESTINATION_ADDRESS_MAX_LEN];
  uint64_t send_amount_sats;    // 0 if no external destination
  uint64_t change_amount_sats;  // 0 if no change output
  uint64_t fee_amount_sats;
} psbt_info_t;

typedef struct {
  uint64_t amount_sats;
} psbt_tx_input_info_t;

typedef struct {
  uint64_t amount_sats;
  const uint8_t* script_pubkey;
  size_t script_pubkey_len;
  bool has_keypath;  // mirrors PSBT output classification; true => change output
} psbt_tx_output_info_t;

// PSBT signing constraints for P2WSH 2-of-3 multisig.
#define PSBT_P2WSH_PUBKEY_LEN             33
#define PSBT_P2WSH_MAX_KEYPATHS           3
#define PSBT_P2WSH_WITNESS_SCRIPT_MAX_LEN 128
#define PSBT_P2WSH_SCRIPTPUBKEY_MAX_LEN   34
#define PSBT_BIP32_PATH_MAX_LEN           10
#define PSBT_SIGHASH_ALL                  0x01
#define PSBT_DER_SIGNATURE_MAX_LEN        72
#define PSBT_SIGNATURE_MAX_LEN            (PSBT_DER_SIGNATURE_MAX_LEN + 1)

typedef struct {
  uint8_t pubkey[PSBT_P2WSH_PUBKEY_LEN];
  uint8_t fingerprint[BIP32_KEY_FINGERPRINT_SIZE];
  uint32_t path[PSBT_BIP32_PATH_MAX_LEN];
  size_t path_len;
} psbt_keypath_t;

typedef struct {
  size_t keypath_count;
  psbt_keypath_t keypaths[PSBT_P2WSH_MAX_KEYPATHS];
  uint8_t witness_script[PSBT_P2WSH_WITNESS_SCRIPT_MAX_LEN];
  size_t witness_script_len;
  uint8_t sighash[SHA256_DIGEST_SIZE];
  uint32_t sighash_type;
} psbt_p2wsh_signing_data_t;

bool psbt_lib_init(void);

/**
 * Parse a PSBT and extract transaction information.
 *
 * Supports the following PSBT shapes:
 * - X inputs, 1 external output, 1 change output
 * - X inputs, 1 change output only (consolidation/sweep to self)
 * - X inputs, 1 external output only (no change)
 *
 * Change outputs are identified by the presence of BIP32 keypath data.
 * External outputs (destinations) are outputs without keypath data.
 *
 * @param psbt_bytes The raw PSBT bytes.
 * @param psbt_len The length of the PSBT bytes.
 * @param network The network for address encoding (mainnet/testnet/regtest).
 * @param info_out Output parameter for the parsed PSBT information.
 * @return PSBT_OK on success, otherwise an error.
 */
psbt_error_t psbt_get_info(const uint8_t* psbt_bytes, size_t psbt_len, ew_network_t network,
                           psbt_info_t* info_out);

/**
 * Build transaction summary info from raw tx fields (non-PSBT flow).
 *
 * Supports the same output shape constraints as psbt_get_info():
 * - X inputs, 1 external output, 1 change output
 * - X inputs, 1 change output only (consolidation/sweep to self)
 * - X inputs, 1 external output only (no change)
 *
 * Change outputs are identified by has_keypath=true.
 * External outputs are outputs with has_keypath=false.
 *
 * @param inputs Input amount metadata.
 * @param input_count Number of inputs.
 * @param outputs Output metadata including scriptPubKey and classification.
 * @param output_count Number of outputs.
 * @param network The network for address encoding (mainnet/testnet/regtest).
 * @param info_out Output parameter for computed transaction info.
 * @return PSBT_OK on success, otherwise an error.
 */
psbt_error_t psbt_get_info_from_tx_fields(const psbt_tx_input_info_t* inputs, size_t input_count,
                                          const psbt_tx_output_info_t* outputs, size_t output_count,
                                          ew_network_t network, psbt_info_t* info_out);

/**
 * Convert a compact ECDSA signature to DER encoding.
 *
 * @param sig Compact signature (64 bytes).
 * @param sig_len Length of the compact signature.
 * @param der_out Output buffer for DER signature.
 * @param der_out_len Size of the output buffer.
 * @param der_len_out Output parameter for DER signature length.
 * @return PSBT_OK on success, otherwise an error.
 */
psbt_error_t psbt_compact_sig_to_der(const uint8_t* sig, size_t sig_len, uint8_t* der_out,
                                     size_t der_out_len, size_t* der_len_out);

// ---------------------------------------------------------------------------
// Non-PSBT transaction helpers
// ---------------------------------------------------------------------------

// Raw transaction input for non-PSBT signing.
typedef struct {
  uint8_t prev_txid[32];  // Previous txid (internal byte order)
  uint32_t prev_index;    // Output index in the previous tx
  uint32_t sequence;      // Sequence number
  uint64_t amount;        // UTXO value in satoshis
  uint32_t derivation_path[PSBT_BIP32_PATH_MAX_LEN];
  size_t derivation_path_len;
} raw_tx_input_t;

// Raw transaction output for non-PSBT signing.
typedef struct {
  uint64_t amount;              // Output value in satoshis
  uint8_t destination_spk[64];  // scriptPubKey (P2WSH is 34 bytes)
  size_t destination_spk_len;
  bool has_derivation_path;  // true for change outputs
  uint32_t derivation_path[PSBT_BIP32_PATH_MAX_LEN];
  size_t derivation_path_len;
} raw_tx_output_t;

#define RAW_TX_MAX_INPUTS  5
#define RAW_TX_MAX_OUTPUTS 2

/**
 * Compute transaction summary from raw tx fields (non-PSBT equivalent of psbt_get_info).
 *
 * Classifies outputs as change (has_derivation_path) or external (no derivation path),
 * and computes destination, amounts, and fee based on that classification.
 *
 * @param inputs Array of raw transaction inputs.
 * @param num_inputs Number of inputs.
 * @param outputs Array of raw transaction outputs.
 * @param num_outputs Number of outputs.
 * @param network Network for address encoding.
 * @param info_out Output parameter for the parsed transaction information.
 * @return PSBT_OK on success, otherwise an error.
 */
psbt_error_t raw_tx_get_info(const raw_tx_input_t* inputs, size_t num_inputs,
                             const raw_tx_output_t* outputs, size_t num_outputs,
                             ew_network_t network, psbt_info_t* info_out);

/**
 * Compute a session commitment hash over all canonical non-PSBT transaction fields.
 *
 * Produces a SHA-256 digest that deterministically covers every field that is
 * used during signing: version, lock_time, and per-input/output details
 * including amounts, scriptPubKeys, and derivation paths.
 *
 * The hash is stored in the confirmation manager when the user approves a
 * transaction.  Before signing, the firmware recomputes the hash from the
 * live signing-session fields and rejects any mismatch, ensuring that the
 * bytes signed are bit-for-bit identical to what was displayed to the user.
 *
 * Hash preimage layout (all values in host byte order):
 *   version (u32) || lock_time (u32) || num_inputs (u32)
 *   for each input:
 *     prev_txid[32] || prev_index (u32) || sequence (u32) || amount (u64)
 *     derivation_path_len (u32) || derivation_path[0..len-1] (u32[])
 *   num_outputs (u32)
 *   for each output:
 *     amount (u64) || destination_spk_len (u32) || destination_spk[0..len-1]
 *     has_derivation_path (u8)
 *     [if has_derivation_path]
 *       derivation_path_len (u32) || derivation_path[0..len-1] (u32[])
 *
 * @param inputs       Array of raw transaction inputs.
 * @param num_inputs   Number of inputs.
 * @param outputs      Array of raw transaction outputs.
 * @param num_outputs  Number of outputs.
 * @param lock_time    Transaction locktime.
 * @param version      Transaction version.
 * @param commitment_out  Output buffer for the 32-byte commitment hash.
 * @return true on success, false if the hash streaming fails.
 */
bool raw_tx_session_commitment_hash(const raw_tx_input_t* inputs, size_t num_inputs,
                                    const raw_tx_output_t* outputs, size_t num_outputs,
                                    uint32_t lock_time, uint32_t version,
                                    uint8_t commitment_out[SHA256_DIGEST_SIZE]);

/**
 * Compute signing data for a P2WSH 2-of-3 input from raw transaction fields.
 *
 * Builds the witness script from the provided child pubkeys and computes the
 * BIP143 sighash. Used by W3 signing flows where pubkeys are derived from the
 * persisted keyset rather than read from a PSBT.
 *
 * @param inputs Array of raw transaction inputs.
 * @param num_inputs Number of inputs.
 * @param outputs Array of raw transaction outputs.
 * @param num_outputs Number of outputs.
 * @param lock_time Transaction locktime.
 * @param version Transaction version (e.g. 1 or 2), used in BIP143 sighash preimage.
 * @param input_index Index of the input to compute signing data for.
 * @param keyset_pubkeys Concatenated 3 x 33-byte compressed pubkeys (app, hw, server)
 *        at the child derivation matching this input's path.
 * @param signing_data_out Output for parsed signing data.
 * @return PSBT_OK on success, otherwise an error.
 */
psbt_error_t raw_tx_p2wsh_input_signing_data(const raw_tx_input_t* inputs, size_t num_inputs,
                                             const raw_tx_output_t* outputs, size_t num_outputs,
                                             uint32_t lock_time, uint32_t version,
                                             size_t input_index, const uint8_t* keyset_pubkeys,
                                             psbt_p2wsh_signing_data_t* signing_data_out);

/**
 * Compute signing data for a P2WSH input using precomputed BIP143 intermediate hashes.
 *
 * This is optimized for streaming signing where hashPrevouts, hashSequence, and
 * hashOutputs have been computed incrementally during payload streaming, avoiding
 * the need to hold all inputs/outputs in RAM simultaneously.
 *
 * @param hash_prevouts Precomputed BIP143 hashPrevouts (32 bytes).
 * @param hash_sequence Precomputed BIP143 hashSequence (32 bytes).
 * @param hash_outputs Precomputed BIP143 hashOutputs (32 bytes).
 * @param lock_time Transaction locktime.
 * @param version Transaction version.
 * @param input The single input to compute signing data for.
 * @param keyset_pubkeys Concatenated 3 x 33-byte compressed pubkeys (app, hw, server).
 * @param signing_data_out Output for parsed signing data.
 * @return PSBT_OK on success, otherwise an error.
 */
psbt_error_t raw_tx_p2wsh_input_signing_data_precomputed(
  const uint8_t hash_prevouts[SHA256_DIGEST_SIZE], const uint8_t hash_sequence[SHA256_DIGEST_SIZE],
  const uint8_t hash_outputs[SHA256_DIGEST_SIZE], uint32_t lock_time, uint32_t version,
  const raw_tx_input_t* input, const uint8_t* keyset_pubkeys,
  psbt_p2wsh_signing_data_t* signing_data_out);
