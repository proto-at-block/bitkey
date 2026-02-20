#include "wallet_address.h"

#include "bip32.h"
#include "ew.h"
#include "log.h"
#include "wstring.h"

// Cryptographic constants
#define PUBKEY_SIZE_COMPRESSED   (33)
#define CHAINCODE_SIZE           (32)
#define MULTISIG_THRESHOLD       (2)
#define MULTISIG_SIGNERS         (3)
#define MAX_MULTISIG_SCRIPT_SIZE (256)
#define MAX_WITNESS_PROGRAM_SIZE (34)
#define HARDENED_BIT             (0x80000000)

// Helper to derive a child public key from account-level parent
static bool derive_child_pubkey(const uint8_t parent_pubkey[33], const uint8_t parent_chaincode[32],
                                uint32_t child_index, uint8_t child_pubkey_out[33]) {
  bool result = false;

  // Create extended key from components
  extended_key_t parent = {
    .prefix = parent_pubkey[0],
    .chaincode = {0},
  };
  memcpy(parent.key, &parent_pubkey[1], CHAINCODE_SIZE);
  memcpy(parent.chaincode, parent_chaincode, CHAINCODE_SIZE);

  // Derive child (unhardened)
  uint32_t indices[] = {child_index};
  derivation_path_t path = {.indices = indices, .num_indices = 1};

  extended_key_t child = {0};
  if (!bip32_derive_path_pub(&parent, &child, &path)) {
    goto cleanup;
  }

  // Convert back to SEC1 compressed format
  child_pubkey_out[0] = child.prefix;
  memcpy(&child_pubkey_out[1], child.key, CHAINCODE_SIZE);

  result = true;

cleanup:
  // Zero sensitive key material
  bip32_zero_key(&parent);
  bip32_zero_key(&child);
  return result;
}

wallet_res_t wallet_derive_address(const wallet_keyset_t* keyset, uint32_t address_index,
                                   char* address_out, size_t address_len) {
  wallet_res_t result = WALLET_RES_ERR;

  if (!keyset || !address_out || address_len == 0) {
    return WALLET_RES_ERR;
  }

  if (keyset->version != WALLET_KEYSET_VERSION) {
    LOGE("Unsupported keyset version: %d", keyset->version);
    return WALLET_RES_ERR;
  }

  if (address_index >= HARDENED_BIT) {
    LOGE("Invalid address index: only unhardened derivation allowed");
    return WALLET_RES_ERR;
  }

  // 1. Derive child public keys for each of the 3 signers (Bitkey-specific: app, server, hw)
  uint8_t app_child_pubkey[PUBKEY_SIZE_COMPRESSED] = {0};
  uint8_t server_child_pubkey[PUBKEY_SIZE_COMPRESSED] = {0};
  uint8_t hw_child_pubkey[PUBKEY_SIZE_COMPRESSED] = {0};

  if (!derive_child_pubkey(keyset->app.pubkey, keyset->app.chaincode, address_index,
                           app_child_pubkey)) {
    LOGE("Failed to derive app child key");
    goto cleanup;
  }

  if (!derive_child_pubkey(keyset->server.pubkey, keyset->server.chaincode, address_index,
                           server_child_pubkey)) {
    LOGE("Failed to derive server child key");
    goto cleanup;
  }

  if (!derive_child_pubkey(keyset->hw.pubkey, keyset->hw.chaincode, address_index,
                           hw_child_pubkey)) {
    LOGE("Failed to derive hw child key");
    goto cleanup;
  }

  // 2. Create Bitkey-specific 2-of-3 multisig script
  uint8_t pubkeys[MULTISIG_SIGNERS * PUBKEY_SIZE_COMPRESSED];
  memcpy(pubkeys, app_child_pubkey, PUBKEY_SIZE_COMPRESSED);
  memcpy(pubkeys + PUBKEY_SIZE_COMPRESSED, server_child_pubkey, PUBKEY_SIZE_COMPRESSED);
  memcpy(pubkeys + (2 * PUBKEY_SIZE_COMPRESSED), hw_child_pubkey, PUBKEY_SIZE_COMPRESSED);

  // Create 2-of-3 multisig script with BIP67 sorting (Bitkey-specific threshold and key count)
  uint8_t multisig_script[MAX_MULTISIG_SCRIPT_SIZE];
  size_t script_len;
  ew_error_t err = ew_multisig_witness_script_from_pubkeys(
    pubkeys, sizeof(pubkeys), MULTISIG_THRESHOLD, true /* sort_keys */, multisig_script,
    sizeof(multisig_script), &script_len);
  if (err != EW_OK) {
    LOGE("Failed to create multisig script");
    goto cleanup;
  }

  // 3. Create P2WSH witness program (generic)
  uint8_t witness_program[MAX_WITNESS_PROGRAM_SIZE];
  size_t program_len;
  err = ew_p2wsh_scriptpubkey_from_witness(multisig_script, script_len, witness_program,
                                           sizeof(witness_program), &program_len);
  if (err != EW_OK) {
    LOGE("Failed to create witness program");
    goto cleanup;
  }

  // 4. Convert to bech32 address (generic)
  ew_network_t network =
    (keyset->network == NETWORK_MAINNET) ? EW_NETWORK_MAINNET : EW_NETWORK_TESTNET;

  err = ew_script_to_address(witness_program, program_len, network, address_out, address_len);
  if (err != EW_OK) {
    LOGE("Failed to encode address");
    goto cleanup;
  }

  result = WALLET_RES_OK;

cleanup:
  // Zero sensitive key material
  memzero(app_child_pubkey, sizeof(app_child_pubkey));
  memzero(server_child_pubkey, sizeof(server_child_pubkey));
  memzero(hw_child_pubkey, sizeof(hw_child_pubkey));
  memzero(pubkeys, sizeof(pubkeys));
  return result;
}
