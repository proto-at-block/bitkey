#pragma once

#include "wallet.h"

/**
 * Derive Bitkey 2-of-3 multisig P2WSH address from keyset.
 *
 * This is product-specific logic (Bitkey's 2-of-3 scheme), not generic libew.
 *
 * @param keyset Wallet keyset containing account-level keys
 * @param address_index Child address index (0, 1, 2...)
 * @param address_out Buffer for output address string
 * @param address_len Size of address_out buffer
 * @return WALLET_RES_OK on success, WALLET_RES_ERR on failure
 */
wallet_res_t wallet_derive_address(const wallet_keyset_t* keyset, uint32_t address_index,
                                   char* address_out, size_t address_len);

/**
 * Derive the expected P2WSH scriptPubKey for our 2-of-3 multisig wallet policy at a given
 * derivation path.
 *
 * The keyset holds account-level xpubs (app/hw at m/84'/coin'/account', server at depth 0).
 * For app and hw keys, child derivation uses the path suffix after WALLET_KEYSET_ACCOUNT_DEPTH.
 * For the server key, child derivation uses the full path with hardened bits stripped.
 *
 * @param keyset Wallet keyset with account-level xpubs.
 * @param derivation_path Full BIP32 path (e.g. {84', 0', 0', 1, 5} for change address 5).
 * @param derivation_path_len Number of path components.
 * @param scriptpubkey_out Output buffer for the derived P2WSH scriptPubKey.
 * @param scriptpubkey_buf_len Size of scriptpubkey_out (must be >= 34 for P2WSH).
 * @param scriptpubkey_len_out Receives the actual number of bytes written.
 * @return WALLET_RES_OK on success, WALLET_RES_ERR on any failure.
 */
wallet_res_t wallet_derive_p2wsh_scriptpubkey(const wallet_keyset_t* keyset,
                                              const uint32_t* derivation_path,
                                              size_t derivation_path_len, uint8_t* scriptpubkey_out,
                                              size_t scriptpubkey_buf_len,
                                              size_t* scriptpubkey_len_out);

/**
 * Validate that a P2WSH scriptPubKey belongs to the wallet policy for a given derivation path.
 *
 * Derives the expected scriptPubKey from the keyset + path and compares it against the provided
 * scriptPubKey using a constant-time comparison. Use this to prove a "change" output is genuinely
 * ours before hiding it from the user during transaction confirmation.
 *
 * @param keyset Wallet keyset with account-level xpubs.
 * @param derivation_path Full BIP32 derivation path (e.g. {84', 0', 0', 1, 5}).
 * @param derivation_path_len Number of path components.
 * @param scriptpubkey The output scriptPubKey to validate.
 * @param scriptpubkey_len Length of scriptpubkey.
 * @return true if the scriptPubKey matches our wallet policy, false otherwise.
 */
bool wallet_change_output_belongs_to_policy(const wallet_keyset_t* keyset,
                                            const uint32_t* derivation_path,
                                            size_t derivation_path_len, const uint8_t* scriptpubkey,
                                            size_t scriptpubkey_len);
