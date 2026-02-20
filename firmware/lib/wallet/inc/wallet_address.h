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
