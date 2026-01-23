#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

typedef enum {
  EW_OK = 0,
  EW_ERROR_INVALID_PARAM,
  EW_ERROR_INTERNAL,
  EW_ERROR_WALLY_INIT_FAILED,
  EW_ERROR_NOT_INITIALIZED,
  EW_ERROR_INVALID_PSBT,
  EW_ERROR_MISSING_UTXO,
  EW_ERROR_SIGNING_FAILED,
  EW_ERROR_NO_MATCHING_INPUTS,
  EW_ERROR_KEY_MISMATCH,
  EW_ERROR_INVALID_SCRIPT_PUBKEY,
  EW_ERROR_ADDRESS_CONVERSION_FAILED,
} ew_error_t;

typedef enum {
  EW_NETWORK_MAINNET = 0,
  EW_NETWORK_TESTNET = 1,
  EW_NETWORK_REGTEST = 2,
} ew_network_t;

typedef struct ew_psbt ew_psbt_t;

#define EW_SEED_SIZE (32)

typedef bool (*ew_crypto_random_cb_t)(uint8_t* out, size_t len);
typedef void (*ew_secure_memzero_cb_t)(void* p, size_t n);
typedef void* (*ew_malloc_cb_t)(size_t n);
typedef void (*ew_free_cb_t)(void* p);
typedef void (*ew_bzero_cb_t)(void* p, size_t n);

// Signature callback types matching libwally's API
typedef int (*ew_ecdsa_sign_cb_t)(
  const uint8_t* priv_key,  // Private key
  size_t priv_key_len,      // Private key length (32 bytes)
  const uint8_t* bytes,     // Message hash
  size_t bytes_len,         // Message hash length (32 bytes)
  const uint8_t* aux_rand,  // Optional auxiliary randomness or NULL
  size_t aux_rand_len,      // Aux rand length (32 bytes or 0)
  uint32_t flags,           // EC_FLAG_ECDSA, EC_FLAG_SCHNORR, EC_FLAG_GRIND_R, etc.
  uint8_t* bytes_out,       // Output buffer for signature
  size_t len                // Size of output buffer (64 or 72 bytes)
);

typedef int (*ew_ecdsa_verify_cb_t)(const uint8_t* pub_key,  // Public key (33 or 65 bytes)
                                    size_t pub_key_len,      // Public key length
                                    const uint8_t* bytes,    // Message hash (32 bytes)
                                    size_t bytes_len,        // Message hash length
                                    uint32_t flags,          // EC_FLAG_ECDSA or EC_FLAG_SCHNORR
                                    const uint8_t* sig,      // Signature (64 or 72 bytes)
                                    size_t sig_len           // Signature length
);

typedef struct {
  // Must fill 'out' with 'len' cryptographically secure random bytes and
  // return true on success.
  ew_crypto_random_cb_t crypto_random;

  // Must securely wipe 'n' bytes at 'p' (not optimized away).
  ew_secure_memzero_cb_t secure_memzero;

  // Memory management.
  ew_malloc_cb_t malloc;
  ew_free_cb_t free;

  // Optional: Custom ECDSA signing function.
  // If NULL, libwally's built-in signing will be used.
  // Return WALLY_OK (0) on success, WALLY_ERROR on failure.
  ew_ecdsa_sign_cb_t ecdsa_sign;

  // Optional: Custom ECDSA verification function.
  // If NULL, libwally's built-in verification will be used.
  // Return WALLY_OK (0) on success, WALLY_ERROR on failure.
  ew_ecdsa_verify_cb_t ecdsa_verify;
} ew_api_t;

/**
 * Initialize the library. Must be called before any other functions.
 *
 * @param api The platform-specific API.
 * @return EW_OK on success, otherwise an error.
 */
ew_error_t ew_init(const ew_api_t* api);

/**
 * Cleanup the library. Must be called after all other functions.
 *
 * @return EW_OK on success, otherwise an error.
 */
void ew_cleanup(void);

/**
 * Generate a random seed.
 *
 * @param seed_out The destination for the random seed.
 * @return EW_OK on success, otherwise an error.
 */
ew_error_t ew_seed_generate(uint8_t seed_out[EW_SEED_SIZE]);

/**
 * Calculate the maximum size needed for a signed PSBT.
 *
 * This calculates a conservative upper bound for the signed PSBT size
 * based on the number of inputs that could be signed.
 *
 * @param psbt_bytes The PSBT to analyze.
 * @param psbt_len The length of the PSBT.
 * @param size_out Output for the maximum size needed for the signed PSBT.
 * @return EW_OK on success, otherwise an error.
 */
ew_error_t ew_psbt_get_max_signed_size(const uint8_t* psbt_bytes, size_t psbt_len,
                                       size_t* size_out);

/**
 * Sign a PSBT.
 *
 * @param psbt_bytes The PSBT to sign.
 * @param psbt_len The length of the PSBT.
 * @param psbt_out The destination for the signed PSBT.
 * @param psbt_out_size The size of the signed PSBT.
 * @param psbt_out_len The length of the signed PSBT.
 * @param seed The seed to use for signing.
 * @param network_mainnet Whether to use the mainnet network.
 * @return EW_OK on success, otherwise an error.
 */
ew_error_t ew_psbt_sign(const uint8_t* psbt_bytes, size_t psbt_len, uint8_t* psbt_out,
                        size_t psbt_out_size, size_t* psbt_out_len,
                        const uint8_t seed[EW_SEED_SIZE], bool network_mainnet);

/**
 * Convert a script to an address.
 *
 * @param script A pointer to the raw script pubkey bytes.
 * @param script_len The length of the script pubkey.
 * @param network Identifies the blockchain network the address should belong to.
 * @param address_out The address string to write to.
 *        legacy P2PKH <34 chars, segwit P2WPKH <42 chars, segwit P2WSH 62 chars, taproot P2TR
 *        62 chars. Libwally allocates up to 90 chars for the address.
 * @param address_len The size of the output buffer in bytes, including space for the null
 *        terminator (for the longest possible address, at least 91 bytes: 90 chars + '\0').
 * @return EW_OK on success, otherwise an error.
 */
ew_error_t ew_script_to_address(const uint8_t* script, size_t script_len, ew_network_t network,
                                char* address_out, size_t address_len);

/**
 * Parse a PSBT from a base64-encoded string.
 *
 * Uses strict parsing according to the PSBT specification. The returned PSBT
 * must be freed with ew_psbt_free() when no longer needed.
 *
 * @param base64_psbt The base64-encoded PSBT string.
 * @param psbt_out Output parameter for the parsed PSBT.
 * @return EW_OK on success, otherwise an error.
 */
ew_error_t ew_psbt_from_base64(const char* base64_psbt, ew_psbt_t** psbt_out);

/**
 * Free a PSBT wrapper and its underlying wally_psbt.
 *
 * Safe to call with NULL.
 *
 * @param psbt The PSBT to free, or NULL.
 */
void ew_psbt_free(ew_psbt_t* psbt);

/**
 * Get the number of inputs in a PSBT.
 *
 * @param psbt The PSBT.
 * @param num_inputs_out Output parameter for the number of inputs.
 * @return EW_OK on success, otherwise an error.
 */
ew_error_t ew_psbt_get_num_inputs(const ew_psbt_t* psbt, size_t* num_inputs_out);

/**
 * Get the number of outputs in a PSBT.
 *
 * @param psbt The PSBT.
 * @return The number of outputs, or 0 if psbt is NULL or invalid.
 */
size_t ew_psbt_get_num_outputs(const ew_psbt_t* psbt);

/**
 * Get the version number of a PSBT.
 *
 * @param psbt The PSBT.
 * @return The PSBT version (typically 0 or 2), or 0 if psbt is NULL or invalid.
 */
uint32_t ew_psbt_get_version(const ew_psbt_t* psbt);

/**
 * Get the amount for a specific input in a PSBT.
 *
 * Checks both witness_utxo and utxo fields. Returns EW_ERROR_MISSING_UTXO
 * if no amount information is available for the input.
 *
 * @param psbt The PSBT.
 * @param index The zero-based index of the input.
 * @param has_amount_out Output parameter indicating if amount was found.
 * @param amount_out Output parameter for the amount in satoshis (0 if not found).
 * @return EW_OK on success, EW_ERROR_MISSING_UTXO if no UTXO data available,
 *         otherwise an error.
 */
ew_error_t ew_psbt_input_get_amount(const ew_psbt_t* psbt, size_t index, bool* has_amount_out,
                                    uint64_t* amount_out);

/**
 * Get script and amount information for a specific output in a PSBT.
 *
 * Handles both PSBT v0 and v2 formats. For v2, checks PSBT output fields first,
 * then falls back to the embedded transaction if needed.
 *
 * @param psbt The PSBT wrapper.
 * @param index The zero-based index of the output.
 * @param script_out Output parameter for the script pubkey pointer.
 * @param script_len_out Output parameter for the script length.
 * @param has_amount_out Output parameter indicating if amount was found.
 * @param amount_out Output parameter for the amount in satoshis (0 if not found).
 * @return EW_OK on success, otherwise an error.
 */
ew_error_t ew_psbt_output_get_info(const ew_psbt_t* psbt, size_t index, const uint8_t** script_out,
                                   size_t* script_len_out, bool* has_amount_out,
                                   uint64_t* amount_out);

/**
 * Parse a PSBT from a byte array.
 *
 * @param psbt_bytes The PSBT to parse.
 * @param psbt_len The length of the PSBT.
 * @param psbt_out Output parameter for the parsed PSBT.
 * @return EW_OK on success, otherwise an error.
 */
ew_error_t ew_psbt_from_bytes(const uint8_t* psbt_bytes, size_t psbt_len, ew_psbt_t** psbt_out);

/**
 * Check if a PSBT output has BIP32 keypath data attached.
 *
 * Outputs with keypaths are typically change outputs that belong to the wallet.
 * Outputs without keypaths are external destinations.
 *
 * @param psbt The PSBT wrapper.
 * @param index The zero-based index of the output.
 * @param has_keypath_out Output parameter indicating if the output has keypath data.
 * @return EW_OK on success, otherwise an error.
 */
ew_error_t ew_psbt_output_has_keypath(const ew_psbt_t* psbt, size_t index, bool* has_keypath_out);

/**
 * Decode a base64-encoded PSBT to a byte array.
 *
 * @param base64_psbt The base64-encoded PSBT string.
 * @param out The destination for the decoded PSBT bytes.
 * @param out_size The size of the output buffer.
 * @param written The length of the decoded PSBT bytes.
 * @return EW_OK on success, otherwise an error.
 */
ew_error_t ew_base64_to_bytes(const char* base64_psbt, uint8_t* out, size_t out_size,
                              size_t* written);
