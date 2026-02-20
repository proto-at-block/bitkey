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
 * Extract signing data for a P2WSH 2-of-3 input from a parsed PSBT.
 *
 * Builds the BIP67-sorted witness script from the input keypaths and validates
 * the input witness_utxo scriptPubKey matches the P2WSH of that script.
 *
 * @param psbt Parsed PSBT wrapper.
 * @param input_index Zero-based input index.
 * @param signing_data_out Output for parsed signing data.
 * @return PSBT_OK on success, otherwise an error.
 */
psbt_error_t psbt_p2wsh_input_signing_data_from_psbt(ew_psbt_t* psbt, size_t input_index,
                                                     psbt_p2wsh_signing_data_t* signing_data_out);

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
