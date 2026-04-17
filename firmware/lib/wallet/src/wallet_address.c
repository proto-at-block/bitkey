#include "wallet_address.h"

#include "bip32.h"
#include "ew.h"
#include "log.h"
#include "wstring.h"

#include <string.h>

// Cryptographic constants
#define PUBKEY_SIZE_COMPRESSED   (33)
#define CHAINCODE_SIZE           (32)
#define MULTISIG_THRESHOLD       (2)
#define MULTISIG_SIGNERS         (3)
#define MAX_MULTISIG_SCRIPT_SIZE (256)
#define MAX_WITNESS_PROGRAM_SIZE (34)
#define HARDENED_BIT             (0x80000000)
#define BIP84_PURPOSE            (84u | HARDENED_BIT)
#define BIP84_COIN_MAINNET       (0u | HARDENED_BIT)
#define BIP84_COIN_TESTNET       (1u | HARDENED_BIT)

// Minimum derivation path length for change output validation.
// BIP84 paths are: m/84'/coin'/acct'/change/idx — 5 components.
// Equivalently: WALLET_KEYSET_ACCOUNT_DEPTH (3) + change (1) + index (1).
#define WALLET_MIN_CHANGE_PATH_LEN (WALLET_KEYSET_ACCOUNT_DEPTH + 2u)

// Maximum derivation path length for change output validation.
// Matches PSBT_BIP32_PATH_MAX_LEN from psbt.h to avoid a cross-library dependency.
#define WALLET_MAX_CHANGE_PATH_LEN (10u)

// Derive a child public key by applying the suffix of full_path starting at account_depth.
// The parent xpub (pubkey + chaincode) is assumed to be at 'account_depth' in the BIP32 tree.
// For the server key (depth 0), pass account_depth=0 and a non-hardened full path.
// Returns false if full_path_len <= account_depth (no child levels to derive).
static bool derive_child_pubkey_for_output(const uint8_t parent_pubkey[PUBKEY_SIZE_COMPRESSED],
                                           const uint8_t parent_chaincode[CHAINCODE_SIZE],
                                           const uint32_t* full_path, size_t full_path_len,
                                           size_t account_depth,
                                           uint8_t pubkey_out[PUBKEY_SIZE_COMPRESSED]) {
  if (full_path_len <= account_depth) {
    return false;
  }

  extended_key_t parent = {
    .prefix = parent_pubkey[0],
  };
  memcpy(parent.key, &parent_pubkey[1], BIP32_KEY_SIZE);
  memcpy(parent.chaincode, parent_chaincode, CHAINCODE_SIZE);

  derivation_path_t path = {
    // cast away const: bip32_derive_path_pub reads but does not modify indices
    .indices = (uint32_t*)&full_path[account_depth],
    .num_indices = full_path_len - account_depth,
  };

  extended_key_t child = {0};
  bool ok = bip32_derive_path_pub(&parent, &child, &path);
  if (ok) {
    pubkey_out[0] = child.prefix;
    memcpy(&pubkey_out[1], child.key, BIP32_KEY_SIZE);
  }

  bip32_zero_key(&parent);
  bip32_zero_key(&child);
  return ok;
}

wallet_res_t wallet_derive_p2wsh_scriptpubkey(const wallet_keyset_t* keyset,
                                              const uint32_t* derivation_path,
                                              size_t derivation_path_len, uint8_t* scriptpubkey_out,
                                              size_t scriptpubkey_buf_len,
                                              size_t* scriptpubkey_len_out) {
  if (!keyset || !derivation_path || !scriptpubkey_out || scriptpubkey_buf_len == 0 ||
      !scriptpubkey_len_out) {
    return WALLET_RES_ERR;
  }

  if (keyset->version != WALLET_KEYSET_VERSION) {
    LOGE("Bad keyset ver: %d", keyset->version);
    return WALLET_RES_ERR;
  }

  if (derivation_path_len < WALLET_MIN_CHANGE_PATH_LEN ||
      derivation_path_len > WALLET_MAX_CHANGE_PATH_LEN) {
    LOGE("Bad path len: %zu", derivation_path_len);
    return WALLET_RES_ERR;
  }

  // Validate BIP84 path prefix: m/84'/coin_type'/account'.
  // The keyset's app/hw keys are stored at account depth, so we skip path[0..2] during
  // derivation. Without this check, a crafted prefix (wrong account or coin type) would
  // produce a scriptPubKey that passes policy validation but creates a UTXO that can't be
  // spent through the normal signing flow.
  const uint32_t expected_coin =
    (keyset->network == NETWORK_MAINNET) ? BIP84_COIN_MAINNET : BIP84_COIN_TESTNET;
  if (derivation_path[0] != BIP84_PURPOSE) {
    LOGE("Bad purpose: 0x%lx", (unsigned long)derivation_path[0]);
    return WALLET_RES_ERR;
  }
  if (derivation_path[1] != expected_coin) {
    LOGE("Bad coin: 0x%lx", (unsigned long)derivation_path[1]);
    return WALLET_RES_ERR;
  }
  if (!(derivation_path[2] & HARDENED_BIT)) {
    LOGE("Acct not hardened");
    return WALLET_RES_ERR;
  }
  // Validate that derivation path account matches the stored keyset account to prevent
  // "chimeric" scriptPubKeys (app/hw keys from one account + server key from another).
  uint32_t path_account = derivation_path[2] & ~HARDENED_BIT;
  if (path_account != keyset->account_index) {
    LOGE("Acct: %lu!=%u", (unsigned long)path_account, (unsigned)keyset->account_index);
    return WALLET_RES_ERR;
  }
  // Change (path[3]) must be 0 (receive) or 1 (change), unhardened.
  if (derivation_path[3] > 1) {
    LOGE("Bad change level: %lu", (unsigned long)derivation_path[3]);
    return WALLET_RES_ERR;
  }
  // All path components from the change level onward must be unhardened. Hardened
  // indices would cause bip32_derive_path_pub to fail later; reject them early with
  // a clear error message.
  for (size_t i = 3; i < derivation_path_len; i++) {
    if (derivation_path[i] & HARDENED_BIT) {
      LOGE("Path[%zu] hardened", i);
      return WALLET_RES_ERR;
    }
  }

  // App and hw xpubs are stored at account depth WALLET_KEYSET_ACCOUNT_DEPTH (m/84'/coin'/acct').
  // Server xpub is stored at depth 0; derive using the full non-hardened path.
  const size_t app_hw_depth = WALLET_KEYSET_ACCOUNT_DEPTH;

  // Build non-hardened version of the full path for the server key.
  // The server always creates its spending key at account 0, so override path[2]
  // (account) to 0 rather than inheriting the HW account index from derivation_path.
  // Without this, post-recovery keysets (HW account > 0) derive server child keys
  // at the wrong account, causing "policy mismatch" errors during signing.
  uint32_t server_path[WALLET_MAX_CHANGE_PATH_LEN];
  for (size_t i = 0; i < derivation_path_len; i++) {
    server_path[i] = derivation_path[i] & ~(uint32_t)BIP32_HARDENED_BIT;
  }
  server_path[2] = 0;  // server account is always 0

  uint8_t app_child[PUBKEY_SIZE_COMPRESSED] = {0};
  uint8_t hw_child[PUBKEY_SIZE_COMPRESSED] = {0};
  uint8_t server_child[PUBKEY_SIZE_COMPRESSED] = {0};
  uint8_t pubkeys[MULTISIG_SIGNERS * PUBKEY_SIZE_COMPRESSED] = {0};
  wallet_res_t result = WALLET_RES_ERR;

  if (!derive_child_pubkey_for_output(keyset->app.pubkey, keyset->app.chaincode, derivation_path,
                                      derivation_path_len, app_hw_depth, app_child)) {
    LOGE("App key err");
    goto cleanup;
  }

  if (!derive_child_pubkey_for_output(keyset->hw.pubkey, keyset->hw.chaincode, derivation_path,
                                      derivation_path_len, app_hw_depth, hw_child)) {
    LOGE("HW key err");
    goto cleanup;
  }

  if (!derive_child_pubkey_for_output(keyset->server.pubkey, keyset->server.chaincode, server_path,
                                      derivation_path_len, 0, server_child)) {
    LOGE("Srv key err");
    goto cleanup;
  }

  // Match the pubkey order used by wallet_derive_address: app, server, hw.
  memcpy(pubkeys, app_child, PUBKEY_SIZE_COMPRESSED);
  memcpy(pubkeys + PUBKEY_SIZE_COMPRESSED, server_child, PUBKEY_SIZE_COMPRESSED);
  memcpy(pubkeys + 2 * PUBKEY_SIZE_COMPRESSED, hw_child, PUBKEY_SIZE_COMPRESSED);

  {
    uint8_t multisig_script[MAX_MULTISIG_SCRIPT_SIZE];
    size_t script_len = 0;
    ew_error_t err = ew_multisig_witness_script_from_pubkeys(
      pubkeys, sizeof(pubkeys), MULTISIG_THRESHOLD, true /* BIP67 sort */, multisig_script,
      sizeof(multisig_script), &script_len);
    if (err != EW_OK) {
      LOGE("Multisig err");
      goto cleanup;
    }

    err = ew_p2wsh_scriptpubkey_from_witness(multisig_script, script_len, scriptpubkey_out,
                                             scriptpubkey_buf_len, scriptpubkey_len_out);
    if (err != EW_OK) {
      LOGE("P2WSH err");
      goto cleanup;
    }
  }

  result = WALLET_RES_OK;

cleanup:
  memzero(app_child, sizeof(app_child));
  memzero(hw_child, sizeof(hw_child));
  memzero(server_child, sizeof(server_child));
  memzero(pubkeys, sizeof(pubkeys));
  return result;
}

bool wallet_change_output_belongs_to_policy(const wallet_keyset_t* keyset,
                                            const uint32_t* derivation_path,
                                            size_t derivation_path_len, const uint8_t* scriptpubkey,
                                            size_t scriptpubkey_len) {
  if (!keyset || !derivation_path || !scriptpubkey || scriptpubkey_len == 0) {
    return false;
  }

  uint8_t expected_spk[MAX_WITNESS_PROGRAM_SIZE] = {0};
  size_t expected_spk_len = 0;

  if (wallet_derive_p2wsh_scriptpubkey(keyset, derivation_path, derivation_path_len, expected_spk,
                                       sizeof(expected_spk), &expected_spk_len) != WALLET_RES_OK) {
    return false;
  }

  if (scriptpubkey_len != expected_spk_len) {
    return false;
  }

  // Constant-time comparison to avoid leaking information about expected scriptPubKey via timing.
  return memcmp_s(scriptpubkey, expected_spk, expected_spk_len) == 0;
}

wallet_res_t wallet_derive_address(const wallet_keyset_t* keyset, uint32_t address_index,
                                   char* address_out, size_t address_len) {
  if (!keyset || !address_out || address_len == 0) {
    return WALLET_RES_ERR;
  }

  if (address_index >= HARDENED_BIT) {
    LOGE("Bad addr idx");
    return WALLET_RES_ERR;
  }

  // Build the full BIP84 receive path: m / 84' / coin' / account' / 0 / address_index.
  // The external chain index (0) is required to match the app's BDK descriptor which
  // derives receive addresses at <account_xpub>/0/*.
  const uint32_t expected_coin =
    (keyset->network == NETWORK_MAINNET) ? BIP84_COIN_MAINNET : BIP84_COIN_TESTNET;
  uint32_t derivation_path[] = {
    BIP84_PURPOSE,                         // 84'
    expected_coin,                         // 0' (mainnet) or 1' (testnet)
    keyset->account_index | HARDENED_BIT,  // account' (from keyset)
    0u,                                    // 0  (external / receive chain)
    address_index,                         // address index
  };
  const size_t derivation_path_len = sizeof(derivation_path) / sizeof(derivation_path[0]);

  // Derive the P2WSH scriptpubkey using the validated multi-depth derivation in
  // wallet_derive_p2wsh_scriptpubkey, which correctly handles the different storage
  // depths for app/hw keys (account depth 3) and the server key (depth 0).
  uint8_t scriptpubkey[MAX_WITNESS_PROGRAM_SIZE] = {0};
  size_t scriptpubkey_len = 0;
  wallet_res_t res =
    wallet_derive_p2wsh_scriptpubkey(keyset, derivation_path, derivation_path_len, scriptpubkey,
                                     sizeof(scriptpubkey), &scriptpubkey_len);
  if (res != WALLET_RES_OK) {
    return res;
  }

  // Convert scriptpubkey to bech32 address
  ew_network_t network =
    (keyset->network == NETWORK_MAINNET) ? EW_NETWORK_MAINNET : EW_NETWORK_TESTNET;
  ew_error_t err =
    ew_script_to_address(scriptpubkey, scriptpubkey_len, network, address_out, address_len);
  if (err != EW_OK) {
    LOGE("Addr encode fail");
    return WALLET_RES_ERR;
  }

  return WALLET_RES_OK;
}
