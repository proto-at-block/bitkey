#include "bip32.h"
#include "confirmation_manager.h"
#include "display.pb.h"
#include "ecc.h"
#include "eek_restoration_impl.h"
#include "ew.h"
#include "filesystem.h"
#include "full_account_cloud_backup_restoration_impl.h"
#include "fwup.h"
#include "grant_protocol.h"
#include "hash.h"
#include "ipc.h"
#include "key_manager_task_impl.h"
#include "log.h"
#include "lost_app_recovery_impl.h"
#include "onboarding.h"
#include "proto_helpers.h"
#include "psbt.h"
#include "psbt_signing.h"
#include "recovery_composites_impl.h"
#include "rotate_app_auth_keys_impl.h"
#include "rtos.h"
#include "secure_channel.h"
#include "sign_action_proof_core.h"
#include "uc.h"
#include "uc_route.h"
#include "ui_events.h"
#include "ui_messaging.h"
#include "upgrade_rotate_app_auth_keys_impl.h"
#include "uxc.pb.h"
#include "wallet.h"
#include "wallet.pb.h"
#include "wallet_address.h"
#include "wsm_integrity_key.h"
#include "wstring.h"

#include <inttypes.h>
#include <string.h>

static void _key_manager_task_handle_uxc_session_response(void* proto, void* UNUSED(context)) {
  UC_IPC_FORWARD(key_manager_port, proto, sizeof(proto), IPC_KEY_MANAGER_UXC_SESSION_RESPONSE);
}

NO_OPTIMIZE void key_manager_task_handle_uxc_session_response(ipc_ref_t* message) {
  fwpb_uxc_msg_device* msg_device = message->object;
  fwpb_secure_channel_establish_rsp* cmd = &msg_device->msg.secure_channel_response;

  volatile uint32_t protocol_version = cmd->protocol_version;
  SECURE_IF_FAILIN(!secure_channel_protocol_version_supported(protocol_version)) {
    LOGE("Bad proto ver: %" PRIu32, protocol_version);
    uc_free_recv_proto(message->object);
    return;
  }

  fwpb_uxc_msg_host* rsp_msg = uc_alloc_send_proto();
  rsp_msg->which_msg = fwpb_uxc_msg_host_secure_channel_confirm_tag;
  fwpb_secure_channel_establish_confirm* rsp = &rsp_msg->msg.secure_channel_confirm;
  secure_channel_err_t ret = secure_uart_channel_establish(
    cmd->pk_device.bytes, cmd->pk_device.size, NULL, NULL, rsp->exchange_sig.bytes,
    sizeof(rsp->exchange_sig.bytes), rsp->key_confirmation_tag.bytes);
  if (ret != SECURE_CHANNEL_OK) {
    LOGE("UXC SC establish fail: %d", ret);
    uc_free_send_proto(rsp_msg);
    uc_free_recv_proto(message->object);
    return;
  }

  ret = secure_uart_channel_confirm_session(cmd->key_confirmation_tag.bytes,
                                            cmd->exchange_sig.bytes, cmd->exchange_sig.size);
  uc_free_recv_proto(message->object);
  if (ret != SECURE_CHANNEL_OK) {
    LOGE("UXC SC confirm fail: %d", ret);
    uc_free_send_proto(rsp_msg);
    return;
  }

  rsp->exchange_sig.size = sizeof(rsp->exchange_sig.bytes);
  rsp->key_confirmation_tag.size = sizeof(rsp->key_confirmation_tag.bytes);
  rsp->protocol_version = SECURE_CHANNEL_PROTOCOL_VERSION;

  (void)uc_send(rsp_msg);
}

void key_manager_task_handle_uxc_session_init(void) {
  // Clear any stale session state from a prior NFC session
  lost_app_recovery_clear_session();
  rotate_app_auth_keys_clear_session();
  upgrade_rotate_app_auth_keys_clear_session();
  recovery_composites_clear_sessions();
  eek_restoration_clear_session();
  full_account_cloud_backup_restoration_clear_session();

  fwpb_uxc_msg_host* msg = uc_alloc_send_proto();
  ASSERT(msg != NULL);
  msg->which_msg = fwpb_uxc_msg_host_secure_channel_establish_tag;
  fwpb_secure_channel_establish_cmd* secure_channel_establish_cmd =
    &msg->msg.secure_channel_establish;

  uint8_t public_key_bytes[SECURE_CHANNEL_PUBKEY_MAX_LEN] = {0};
  uint32_t public_key_len = sizeof(public_key_bytes);
  secure_channel_err_t err = secure_uart_channel_public_key_init(public_key_bytes, &public_key_len);
  if (err != SECURE_CHANNEL_OK) {
    LOGE("SC pk init: %d", err);
    uc_free_send_proto(msg);
    return;
  }
  PROTO_FILL_BYTES(secure_channel_establish_cmd, pk_host, public_key_bytes, public_key_len);
  secure_channel_establish_cmd->protocol_version = SECURE_CHANNEL_PROTOCOL_VERSION;
  (void)uc_send(msg);
}

// WSM Integrity message construction constants
#define WSM_INTEGRITY_LABEL     "WsmIntegrityV1"
#define WSM_INTEGRITY_LABEL_LEN (14)
#define WSM_SIGN_KEYS_LABEL     "SignPublicKeysV1"
#define WSM_SIGN_KEYS_LABEL_LEN (16)
#define WSM_NUM_PUBKEYS         (5)
#define WSM_PAYLOAD_SIZE        (WSM_NUM_PUBKEYS * PUBKEY_LENGTH)
#define WSM_MESSAGE_SIZE        (WSM_INTEGRITY_LABEL_LEN + WSM_SIGN_KEYS_LABEL_LEN + WSM_PAYLOAD_SIZE)

// Helper: Validate a secp256k1 public key
static bool validate_pubkey(const uint8_t pubkey[PUBKEY_LENGTH]) {
  // Check compression byte is valid (0x02 or 0x03)
  if (pubkey[0] != SEC1_COMPRESSED_PUBKEY_EVEN && pubkey[0] != SEC1_COMPRESSED_PUBKEY_ODD) {
    return false;
  }

  // Validate the pubkey is a valid secp256k1 curve point
  return crypto_ecc_secp256k1_pubkey_verify(pubkey);
}

// Helper: Verify WSM signature over 5 pubkeys
static bool verify_wsm_signature(const uint8_t* app_auth_pub, const uint8_t* hw_auth_pub,
                                 const uint8_t* app_spending_pub, const uint8_t* hw_spending_pub,
                                 const uint8_t* server_spending_pub, const uint8_t* signature,
                                 size_t signature_len) {
  // Validate input parameters
  if (!app_auth_pub || !hw_auth_pub || !app_spending_pub || !hw_spending_pub ||
      !server_spending_pub || !signature) {
    LOGE("WSM null");
    return false;
  }

  if (signature_len != ECC_SIG_SIZE) {
    LOGE("Bad sig sz: %zu", signature_len);
    return false;
  }

  // Build message: "WsmIntegrityV1" || "SignPublicKeysV1" || 5_pubkeys
  uint8_t message[WSM_MESSAGE_SIZE];
  size_t offset = 0;
  memcpy(&message[offset], WSM_INTEGRITY_LABEL, WSM_INTEGRITY_LABEL_LEN);
  offset += WSM_INTEGRITY_LABEL_LEN;
  memcpy(&message[offset], WSM_SIGN_KEYS_LABEL, WSM_SIGN_KEYS_LABEL_LEN);
  offset += WSM_SIGN_KEYS_LABEL_LEN;
  memcpy(&message[offset], app_auth_pub, PUBKEY_LENGTH);
  offset += PUBKEY_LENGTH;
  memcpy(&message[offset], hw_auth_pub, PUBKEY_LENGTH);
  offset += PUBKEY_LENGTH;
  memcpy(&message[offset], app_spending_pub, PUBKEY_LENGTH);
  offset += PUBKEY_LENGTH;
  memcpy(&message[offset], hw_spending_pub, PUBKEY_LENGTH);
  offset += PUBKEY_LENGTH;
  memcpy(&message[offset], server_spending_pub, PUBKEY_LENGTH);

  return wsm_verify_signature(message, WSM_MESSAGE_SIZE, signature);
}

void key_manager_task_port_handle_derive_and_sign(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_derive_and_sign_rsp_tag;
  rsp->status = fwpb_status_FEATURE_NOT_SUPPORTED;

  LOGE("derive_and_sign not supported on W3");
  proto_send_rsp(cmd, rsp);
}

void key_manager_task_port_handle_unseal_csek(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_unseal_csek_rsp_tag;
  rsp->status = fwpb_status_FEATURE_NOT_SUPPORTED;

  LOGE("unseal_csek not supported on W3");
  proto_send_rsp(cmd, rsp);
}

void key_manager_task_port_handle_get_address(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_get_address_rsp_tag;
  rsp->status = fwpb_status_ERROR;

  uint32_t address_index = cmd->msg.get_address_cmd.address_index;

  // 1. Load stored keyset
  wallet_keyset_t keyset = {0};
  if (!wkek_read_and_decrypt(WALLET_KEYSET_PATH, (uint8_t*)&keyset, sizeof(keyset))) {
    LOGE("Keyset load fail");
    rsp->status = fwpb_status_DESCRIPTOR_NOT_LOADED;
    goto out;
  }

  if (keyset.version != WALLET_KEYSET_VERSION) {
    LOGE("Bad keyset v%d", keyset.version);
    rsp->status = fwpb_status_ERROR;
    goto out;
  }

  // 2. Derive and generate P2WSH address from keyset
  char address[128] = {0};
  wallet_res_t res = wallet_derive_address(&keyset, address_index, address, sizeof(address));

  if (res != WALLET_RES_OK) {
    LOGE("Addr fail: %lu", (unsigned long)address_index);
    rsp->status = fwpb_status_ERROR;
    goto out;
  }

  // 3. Display address on screen (sleep extension handled by money_movement flow)
  receive_transaction_data_t ui_data = {0};
  strncpy(ui_data.address, address, sizeof(ui_data.address) - 1);
  UI_SHOW_EVENT_WITH_DATA(UI_EVENT_START_RECEIVE_TRANSACTION, &ui_data, sizeof(ui_data));

  // 4. Return address in response
  strncpy(rsp->msg.get_address_rsp.address, address, sizeof(rsp->msg.get_address_rsp.address) - 1);
  rsp->status = fwpb_status_SUCCESS;

out:
  memzero(address, sizeof(address));
  memzero(&keyset, sizeof(keyset));
  proto_send_rsp(cmd, rsp);
}

void key_manager_task_port_handle_fingerprint_reset_finalize(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_fingerprint_reset_finalize_rsp_tag;
  rsp->status = fwpb_status_FEATURE_NOT_SUPPORTED;

  LOGE("provide_grant (fingerprint_reset_finalize) unsupported on W3");
  proto_send_rsp(cmd, rsp);
}

void key_manager_task_port_handle_verify_keys_and_build_descriptor(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_verify_keys_and_build_descriptor_rsp_tag;
  rsp->status = fwpb_status_ERROR;

  fwpb_verify_keys_and_build_descriptor_cmd* vcmd = &cmd->msg.verify_keys_and_build_descriptor_cmd;

  // 1. Validate and extract app spending key components
  if (vcmd->app_spending_key.size != PUBKEY_LENGTH ||
      vcmd->app_spending_key_chaincode.size != CHAINCODE_LENGTH) {
    LOGE("Bad app spend sz");
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }
  const uint8_t* app_pubkey = vcmd->app_spending_key.bytes;
  const uint8_t* app_chaincode = vcmd->app_spending_key_chaincode.bytes;

  // Map proto network field to keyset network
  uint8_t app_network = vcmd->network_mainnet ? NETWORK_MAINNET : NETWORK_TESTNET;

  // Account index from proto (defaults to 0 for backwards compatibility)
  if (vcmd->account_index > UINT8_MAX) {
    LOGE("VK: acct idx %lu", (unsigned long)vcmd->account_index);
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }
  uint32_t hw_account_index = vcmd->account_index;

  // Validate app spending pubkey
  if (!validate_pubkey(app_pubkey)) {
    LOGE("Bad app spend pk");
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }

  // 2. Validate and extract server pubkey + chaincode
  if (vcmd->server_spending_key.size != PUBKEY_LENGTH ||
      vcmd->server_spending_key_chaincode.size != CHAINCODE_LENGTH) {
    LOGE("Bad srv spend sz");
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }
  const uint8_t* server_pubkey = vcmd->server_spending_key.bytes;
  const uint8_t* server_chaincode = vcmd->server_spending_key_chaincode.bytes;

  // Validate server spending pubkey
  if (!validate_pubkey(server_pubkey)) {
    LOGE("Bad srv spend pk");
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }

  // 3. Validate app auth key
  if (vcmd->app_auth_key.size != PUBKEY_LENGTH) {
    LOGE("Bad app auth sz");
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }

  // Validate app auth pubkey
  if (!validate_pubkey(vcmd->app_auth_key.bytes)) {
    LOGE("Bad app auth pk");
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }

  // 4. Derive hardware keys
  extended_key_t hw_auth_key_priv __attribute__((__cleanup__(bip32_zero_key)));
  if (!wallet_get_w1_auth_key(&hw_auth_key_priv)) {
    LOGE("HW auth fail");
    rsp->status = fwpb_status_FILE_NOT_FOUND;
    goto out;
  }

  uint32_t spending_indices[] = {
    BIP84_PURPOSE | BIP32_HARDENED_BIT,
    (app_network == NETWORK_MAINNET ? BIP32_COIN_BTC : BIP32_COIN_TESTNET) | BIP32_HARDENED_BIT,
    hw_account_index | BIP32_HARDENED_BIT};
  derivation_path_t spending_path = {.indices = spending_indices,
                                     .num_indices = BIP32_PATH_DEPTH_ACCOUNT};

  extended_key_t hw_spending_key_priv __attribute__((__cleanup__(bip32_zero_key)));
  if (!wallet_derive_key_priv_using_cache(&hw_spending_key_priv, spending_path)) {
    LOGE("HW spend fail");
    rsp->status = fwpb_status_FILE_NOT_FOUND;
    goto out;
  }

  extended_key_t hw_auth_key_pub __attribute__((__cleanup__(bip32_zero_key)));
  if (!bip32_priv_to_pub(&hw_auth_key_priv, &hw_auth_key_pub)) {
    LOGE("HW auth pub err");
    rsp->status = fwpb_status_ERROR;
    goto out;
  }

  extended_key_t hw_spending_key_pub __attribute__((__cleanup__(bip32_zero_key)));
  if (!bip32_priv_to_pub(&hw_spending_key_priv, &hw_spending_key_pub)) {
    LOGE("HW spend pub err");
    rsp->status = fwpb_status_ERROR;
    goto out;
  }

  // Build SEC1-encoded pubkeys for WSM verification
  uint8_t hw_auth_pubkey[PUBKEY_LENGTH];
  hw_auth_pubkey[0] = hw_auth_key_pub.prefix;
  memcpy(&hw_auth_pubkey[1], hw_auth_key_pub.key, BIP32_KEY_SIZE);

  uint8_t hw_spending_pubkey[PUBKEY_LENGTH];
  hw_spending_pubkey[0] = hw_spending_key_pub.prefix;
  memcpy(&hw_spending_pubkey[1], hw_spending_key_pub.key, BIP32_KEY_SIZE);

  // Validate HW pubkeys
  if (!validate_pubkey(hw_auth_pubkey) || !validate_pubkey(hw_spending_pubkey)) {
    LOGE("Bad HW pk");
    rsp->status = fwpb_status_ERROR;
    goto out;
  }

  // 5. Verify WSM signature over all 5 public keys
  // Message format: "WsmIntegrityV1" || "SignPublicKeysV1" || app_auth || hw_auth || app_spending
  // || hw_spending || server_spending
  if (!verify_wsm_signature(vcmd->app_auth_key.bytes, hw_auth_pubkey, app_pubkey,
                            hw_spending_pubkey, server_pubkey, vcmd->wsm_signature.bytes,
                            vcmd->wsm_signature.size)) {
    LOGE("WSM verify fail");
    rsp->status = fwpb_status_VERIFICATION_FAILED;
    goto out;
  }

  // 6. Build and store keyset
  wallet_keyset_t keyset = {
    .version = WALLET_KEYSET_VERSION,
    .network = app_network,
    .account_index = (uint8_t)hw_account_index,
  };
  memcpy(keyset.app.pubkey, app_pubkey, PUBKEY_LENGTH);
  memcpy(keyset.app.chaincode, app_chaincode, CHAINCODE_LENGTH);
  memcpy(keyset.hw.pubkey, hw_spending_pubkey, PUBKEY_LENGTH);
  memcpy(keyset.hw.chaincode, hw_spending_key_pub.chaincode, CHAINCODE_LENGTH);
  memcpy(keyset.server.pubkey, server_pubkey, PUBKEY_LENGTH);
  memcpy(keyset.server.chaincode, server_chaincode, CHAINCODE_LENGTH);

  if (!wkek_encrypt_and_store(WALLET_KEYSET_PATH, (const uint8_t*)&keyset, sizeof(keyset))) {
    LOGE("Keyset save fail");
    rsp->status = fwpb_status_STORAGE_ERR;
    goto out;
  }

  // Keyset saved

  // 7. Persist app auth pubkey (same as provision_app_auth_pubkey command)
  {
    grant_protocol_result_t grant_res =
      grant_protocol_provision_app_auth_pubkey(vcmd->app_auth_key.bytes);
    if (grant_res != GRANT_RESULT_OK) {
      LOGE("App auth persist fail: %d", grant_res);
      rsp->status = fwpb_status_STORAGE_ERR;
      goto out;
    }
  }

  // 8. Sign app_auth_key with HW auth private key to produce AppGlobalAuthKeyHwSignature.
  //    This mirrors the W1 SignChallenge path: the app sends the pubkey as a hex string,
  //    which gets SHA256-hashed then signed. We hex-encode the raw pubkey bytes here to
  //    match the same message format.
  {
    // Hex-encode app_auth_key (33 bytes -> 66 chars + null terminator)
    char app_auth_hex[vcmd->app_auth_key.size * 2 + 1];
    for (size_t i = 0; i < vcmd->app_auth_key.size; i++) {
      snprintf(&app_auth_hex[i * 2], 3, "%02x", vcmd->app_auth_key.bytes[i]);
    }

    // SHA256 hash with domain separation: SHA-256("BKRelationshipEndorsement" || hex_string)
#define VK_KEY_DOMAIN_TAG "BKRelationshipEndorsement"
    uint8_t app_auth_hash[SHA256_DIGEST_SIZE];
    hash_stream_ctx_t vk_ctx;
    if (!crypto_sha256_stream_init(&vk_ctx) ||
        !crypto_sha256_stream_update(&vk_ctx, (uint8_t*)VK_KEY_DOMAIN_TAG,
                                     sizeof(VK_KEY_DOMAIN_TAG) - 1) ||
        !crypto_sha256_stream_update(&vk_ctx, (uint8_t*)app_auth_hex, sizeof(app_auth_hex) - 1) ||
        !crypto_sha256_stream_final(&vk_ctx, app_auth_hash)) {
      LOGE("VK hash err");
      rsp->status = fwpb_status_ERROR;
      goto out;
    }
#undef VK_KEY_DOMAIN_TAG

    derivation_path_t* sign_auth_path = wallet_get_w1_auth_path();
    key_manager_sign_result_t sign_res = key_manager_derive_and_sign(
      *sign_auth_path, app_auth_hash,
      rsp->msg.verify_keys_and_build_descriptor_rsp.app_auth_key_signature.bytes);
    memzero(app_auth_hash, sizeof(app_auth_hash));

    switch (sign_res) {
      case KEY_MANAGER_SIGN_SUCCESS:
        rsp->msg.verify_keys_and_build_descriptor_rsp.app_auth_key_signature.size = ECC_SIG_SIZE;
        break;
      default:
        LOGE("VK sign err: %d", sign_res);
        rsp->status = fwpb_status_ERROR;
        goto out;
    }
  }

  rsp->status = fwpb_status_SUCCESS;
  UI_SHOW_EVENT(UI_EVENT_SHOW_ONBOARDING_COMPLETE);

out:
  memzero(&keyset, sizeof(keyset));
  proto_send_rsp(cmd, rsp);
}

// ---------------------------------------------------------------------------
// Streaming signing session (for >5 inputs)
// ---------------------------------------------------------------------------
// Payload chunks are written to a temp file in flash during transfer.
// Only metadata and hash contexts live in RAM (~300 bytes).
// Parse-on-demand helpers read fixed-size records from flash as needed.

#define STREAM_TX_MAX_INPUTS  400
#define STREAM_TX_MAX_OUTPUTS 10

// Wire format record sizes (fixed, zero-padded)
#define STREAM_HEADER_SIZE        16
#define STREAM_INPUT_RECORD_SIZE  69
#define STREAM_OUTPUT_RECORD_SIZE 66

// Flash file paths
#define STREAM_PAYLOAD_PATH "signing_payload"
#define STREAM_SIGS_PATH    "signing_sigs"

// Pre-computed signature record: pubkey(33) + sig_len(1) + signature(73) = 107 bytes
#define SIG_RECORD_PUBKEY_SIZE PSBT_P2WSH_PUBKEY_LEN  // 33
#define SIG_RECORD_SIGLEN_SIZE 1
#define SIG_RECORD_SIG_SIZE    PSBT_SIGNATURE_MAX_LEN  // 73
#define SIG_RECORD_SIZE        (SIG_RECORD_PUBKEY_SIZE + SIG_RECORD_SIGLEN_SIZE + SIG_RECORD_SIG_SIZE)

typedef enum {
  STREAM_STATE_IDLE = 0,
  STREAM_STATE_RECEIVING,  // Chunks being received
  STREAM_STATE_FINALIZED,  // Payload complete, awaiting confirmation
  STREAM_STATE_CONFIRMED,  // User confirmed, ready for signature retrieval
} stream_signing_state_t;

// Sweep signing context shared by one-shot (w3_signing_session_t) and streaming
// (stream_signing_session_t). `active` is set when the session was initiated by
// sweep_sign_cmd / sweep_sign_stream_start_cmd; the signer uses `app_xpub` /
// `server_xpub` in place of keyset.app / keyset.server, and derives HW from
// master at `old_account_index`.
//
// Each account uses distinct app, hw, and server cosigner keys. HW is derivable
// from master; app and server xpubs must be supplied by the app because the
// stored keyset only holds the current account's xpubs.
typedef struct {
  bool active;
  uint32_t old_account_index;
  xpub_t app_xpub;     // OLD account app xpub at depth 3
  xpub_t server_xpub;  // OLD account server xpub at depth 3
  xpub_t hw_xpub;      // OLD account HW xpub at depth 3, derived from master once per request
} sweep_context_t;

typedef struct {
  stream_signing_state_t state;
  // Transaction metadata from start_cmd
  uint32_t num_inputs;
  uint32_t num_outputs;
  uint32_t version;
  uint32_t lock_time;
  uint32_t expected_payload_size;
  // Streaming state
  uint32_t bytes_received;
  uint32_t next_sequence_id;
  // SHA256 commitment hash context (over all received bytes)
  hash_stream_ctx_t commitment_ctx;
  // Precomputed BIP143 intermediate hashes (finalized at finalize_cmd)
  uint8_t hash_prevouts[SHA256_DIGEST_SIZE];
  uint8_t hash_sequence[SHA256_DIGEST_SIZE];
  uint8_t hash_outputs[SHA256_DIGEST_SIZE];
  // Pre-computed signature cache state
  uint32_t sigs_computed;  // Number of signatures written to STREAM_SIGS_PATH
  bool precompute_done;    // All signatures computed successfully
  bool precompute_failed;  // Precomputation hit an error (fall back to on-demand)
  // Display preferences (ephemeral, from sign_stream_start_cmd)
  uint32_t btc_display_unit;  // 0 = satoshi, 1 = bitcoin
  // Payload commitment hash (computed over ALL received bytes at finalize time).
  // Stored for post-approval verification - covers amounts, paths, and all tx fields.
  uint8_t payload_hash[SHA256_DIGEST_SIZE];
  // Sweep signing context (populated iff stream was initiated by
  // sweep_sign_stream_start_cmd). See sweep_context_t docs.
  sweep_context_t sweep;
} stream_signing_session_t;

// ---------------------------------------------------------------------------
// Non-PSBT signing session
// ---------------------------------------------------------------------------
// Key indices within the xpubs/account_depths arrays (must match keyset order)
#define KEY_INDEX_APP    0
#define KEY_INDEX_HW     1
#define KEY_INDEX_SERVER 2
typedef struct {
  bool active;
  // Parsed request fields
  raw_tx_input_t inputs[RAW_TX_MAX_INPUTS];
  size_t num_inputs;
  raw_tx_output_t outputs[RAW_TX_MAX_OUTPUTS];
  size_t num_outputs;
  uint32_t lock_time;
  uint32_t version;  // Transaction version (e.g. 1 or 2)
  // Computed signatures (filled after user confirmation)
  key_manager_psbt_signature_t signatures[RAW_TX_MAX_INPUTS];
  size_t num_signatures;
  fwpb_status sign_result;
  // Sweep signing context (populated iff session was initiated by
  // sweep_sign_cmd). See sweep_context_t docs.
  sweep_context_t sweep;
} w3_signing_session_t;

// ---------------------------------------------------------------------------
// Session commitment binding
// ---------------------------------------------------------------------------
// Bundles the display summary together with a cryptographic commitment over
// the canonical signing fields.  Stored as the confirmation operation data so
// that the confirmation manager holds the ground truth for what the user
// actually approved.  At signing time we recompute the hash from the live
// signing_session and reject any mismatch.
typedef struct {
  psbt_info_t display_info;                  // What was shown on screen
  uint8_t session_hash[SHA256_DIGEST_SIZE];  // SHA-256 of canonical tx fields
} tx_session_confirmation_data_t;

_Static_assert(sizeof(tx_session_confirmation_data_t) <= MAX_OPERATION_DATA_SIZE,
               "tx_session_confirmation_data_t exceeds MAX_OPERATION_DATA_SIZE");

// Thin wrapper: unpack signing_session fields and delegate to the testable
// raw_tx_session_commitment_hash() in psbt.c.
static bool compute_session_commitment_hash(const w3_signing_session_t* session,
                                            uint8_t commitment_out[SHA256_DIGEST_SIZE]) {
  return raw_tx_session_commitment_hash(session->inputs, session->num_inputs, session->outputs,
                                        session->num_outputs, session->lock_time, session->version,
                                        commitment_out);
}

// ---------------------------------------------------------------------------
// Streaming session commitment binding
// ---------------------------------------------------------------------------
// Similar to tx_session_confirmation_data_t but for streaming signing.
// The commitment hash covers the BIP143 intermediate hashes plus version,
// lock_time, and counts — all fields that influence the final signatures.
typedef struct {
  psbt_info_t display_info;                  // What was shown on screen
  uint8_t session_hash[SHA256_DIGEST_SIZE];  // SHA-256 of streaming session fields
} stream_session_confirmation_data_t;

_Static_assert(sizeof(stream_session_confirmation_data_t) <= MAX_OPERATION_DATA_SIZE,
               "stream_session_confirmation_data_t exceeds MAX_OPERATION_DATA_SIZE");

// Compute commitment hash over all streaming session fields used for signing.
// This covers:
//   - BIP143 intermediate hashes (bind to outpoints, sequences, output amounts/scripts)
//   - version, lock_time, num_inputs, num_outputs
//   - payload_hash (binds to ALL flash data including per-input amounts, derivation paths)
//
// The payload_hash is critical because per-input amounts are read from flash at signing
// time and aren't covered by the BIP143 intermediate hashes.  Including it ensures that
// any post-approval flash corruption will be detected.
static bool compute_stream_session_commitment_hash(const stream_signing_session_t* session,
                                                   const uint8_t payload_hash[SHA256_DIGEST_SIZE],
                                                   uint8_t commitment_out[SHA256_DIGEST_SIZE]) {
  hash_stream_ctx_t ctx;
  if (!crypto_sha256_stream_init(&ctx)) {
    return false;
  }
  // Hash all fields that influence the final signatures.
  // BIP143 intermediate hashes (cover all input outpoints, sequences, and outputs)
  if (!crypto_sha256_stream_update(&ctx, (uint8_t*)session->hash_prevouts, SHA256_DIGEST_SIZE) ||
      !crypto_sha256_stream_update(&ctx, (uint8_t*)session->hash_sequence, SHA256_DIGEST_SIZE) ||
      !crypto_sha256_stream_update(&ctx, (uint8_t*)session->hash_outputs, SHA256_DIGEST_SIZE) ||
      // Transaction-level fields
      !crypto_sha256_stream_update(&ctx, (uint8_t*)&session->version, sizeof(session->version)) ||
      !crypto_sha256_stream_update(&ctx, (uint8_t*)&session->lock_time,
                                   sizeof(session->lock_time)) ||
      !crypto_sha256_stream_update(&ctx, (uint8_t*)&session->num_inputs,
                                   sizeof(session->num_inputs)) ||
      !crypto_sha256_stream_update(&ctx, (uint8_t*)&session->num_outputs,
                                   sizeof(session->num_outputs)) ||
      // Payload hash (covers per-input amounts, derivation paths, and all other flash data)
      !crypto_sha256_stream_update(&ctx, (uint8_t*)payload_hash, SHA256_DIGEST_SIZE)) {
    return false;
  }
  return crypto_sha256_stream_final(&ctx, commitment_out);
}

// Recompute payload hash by reading and hashing the flash payload file.
// This is used at signing time to verify flash integrity hasn't changed since finalize.
static bool recompute_payload_hash_from_flash(uint8_t hash_out[SHA256_DIGEST_SIZE]) {
  fs_file_t* file = NULL;
  if (fs_open_global(&file, STREAM_PAYLOAD_PATH, FS_O_RDONLY) != 0) {
    return false;
  }

  hash_stream_ctx_t ctx;
  if (!crypto_sha256_stream_init(&ctx)) {
    fs_close_global(file);
    return false;
  }

  uint8_t buf[256];
  int32_t bytes_read;
  while ((bytes_read = fs_file_read(file, buf, sizeof(buf))) > 0) {
    if (!crypto_sha256_stream_update(&ctx, buf, (size_t)bytes_read)) {
      memzero(buf, sizeof(buf));
      fs_close_global(file);
      return false;  // Hash update failed
    }
  }

  memzero(buf, sizeof(buf));
  fs_close_global(file);

  if (bytes_read < 0) {
    return false;  // Read error
  }

  return crypto_sha256_stream_final(&ctx, hash_out);
}

// ---------------------------------------------------------------------------
// Union: non-streaming, streaming, and SAP sessions share RAM (never concurrent).
// Each handler resets all three before starting its own session.
// ---------------------------------------------------------------------------
static union {
  w3_signing_session_t signing;
  stream_signing_session_t stream;
  sap_session_t sap;
} session_union SHARED_TASK_BSS = {0};

#define signing_session (session_union.signing)
#define stream_session  (session_union.stream)
#define sap_session     (session_union.sap)

static void signing_session_reset(void) {
  memzero(&signing_session, sizeof(signing_session));
}

static void stream_session_reset(void) {
  fs_remove(STREAM_PAYLOAD_PATH);
  fs_remove(STREAM_SIGS_PATH);
  memzero(&stream_session, sizeof(stream_session));
}

// ---------------------------------------------------------------------------
// Parse-on-demand helpers: read fixed-size wire records from flash
// ---------------------------------------------------------------------------

// Read `size` bytes from the payload file at the given offset.
// Self-contained variant: opens/closes the file per call.
// Use for single reads (e.g. get_tx_signature). For batch reads, use
// the _with_file variants below to amortize the open/close.
static bool flash_read_at(uint32_t offset, void* buf, uint32_t size) {
  fs_file_t* file = NULL;
  if (fs_open_global(&file, STREAM_PAYLOAD_PATH, FS_O_RDONLY) != 0)
    return false;
  bool ok = (fs_file_seek(file, (int32_t)offset, FS_SEEK_SET) >= 0) &&
            (fs_file_read(file, buf, size) == (int32_t)size);
  fs_close_global(file);
  return ok;
}

// Read from an already-open file handle (no open/close overhead).
static bool flash_read_at_f(fs_file_t* file, uint32_t offset, void* buf, uint32_t size) {
  return (fs_file_seek(file, (int32_t)offset, FS_SEEK_SET) >= 0) &&
         (fs_file_read(file, buf, size) == (int32_t)size);
}

// Decode an input record from raw bytes into raw_tx_input_t.
static bool decode_input_record(const uint8_t* rec, raw_tx_input_t* out) {
  const uint8_t* p = rec;
  memcpy(out->prev_txid, p, 32);
  p += 32;
  memcpy(&out->prev_index, p, 4);
  p += 4;
  memcpy(&out->sequence, p, 4);
  p += 4;
  memcpy(&out->amount, p, 8);
  p += 8;

  uint8_t path_len = *p;
  p += 1;
  // Streaming records encode exactly 5 path elements; inputs require a full
  // BIP84 derivation path (m/84'/coin'/acct'/change/index).
  if (path_len != 5)
    return false;
  out->derivation_path_len = path_len;
  for (size_t j = 0; j < 5; j++) {
    uint32_t val;
    memcpy(&val, p, 4);
    if (j < path_len)
      out->derivation_path[j] = val;
    p += 4;
  }
  return true;
}

// Decode an output record from raw bytes into raw_tx_output_t.
static bool decode_output_record(const uint8_t* rec, raw_tx_output_t* out) {
  const uint8_t* p = rec;
  memcpy(&out->amount, p, 8);
  p += 8;
  uint8_t spk_len = *p;
  p += 1;
  // The wire record always encodes 35 bytes of scriptPubKey. Reject lengths
  // exceeding either the destination buffer or the wire field to avoid
  // hashing uninitialized bytes.
  if (spk_len > sizeof(out->destination_spk) || spk_len > 35)
    return false;
  out->destination_spk_len = spk_len;
  memcpy(out->destination_spk, p, spk_len);
  p += 35;
  uint8_t has_path = *p;
  p += 1;
  out->has_derivation_path = (has_path != 0);
  uint8_t out_path_len = *p;
  p += 1;
  if (out_path_len > PSBT_BIP32_PATH_MAX_LEN)
    return false;
  out->derivation_path_len = out_path_len;
  for (size_t j = 0; j < 5; j++) {
    uint32_t val;
    memcpy(&val, p, 4);
    if (j < out_path_len)
      out->derivation_path[j] = val;
    p += 4;
  }
  return true;
}

// Single-read convenience wrappers (open/close per call).
static bool parse_input_from_flash(uint32_t index, raw_tx_input_t* out) {
  if (index >= stream_session.num_inputs)
    return false;
  uint8_t rec[STREAM_INPUT_RECORD_SIZE];
  uint32_t offset = STREAM_HEADER_SIZE + index * STREAM_INPUT_RECORD_SIZE;
  if (!flash_read_at(offset, rec, sizeof(rec)))
    return false;
  return decode_input_record(rec, out);
}

// Batch-read variants using an already-open file handle.
static bool parse_input_from_flash_f(fs_file_t* file, uint32_t index, raw_tx_input_t* out) {
  if (index >= stream_session.num_inputs)
    return false;
  uint8_t rec[STREAM_INPUT_RECORD_SIZE];
  uint32_t offset = STREAM_HEADER_SIZE + index * STREAM_INPUT_RECORD_SIZE;
  if (!flash_read_at_f(file, offset, rec, sizeof(rec)))
    return false;
  return decode_input_record(rec, out);
}

static bool parse_output_from_flash_f(fs_file_t* file, uint32_t index, raw_tx_output_t* out) {
  if (index >= stream_session.num_outputs)
    return false;
  uint8_t rec[STREAM_OUTPUT_RECORD_SIZE];
  uint32_t offset = STREAM_HEADER_SIZE + stream_session.num_inputs * STREAM_INPUT_RECORD_SIZE +
                    index * STREAM_OUTPUT_RECORD_SIZE;
  if (!flash_read_at_f(file, offset, rec, sizeof(rec)))
    return false;
  return decode_output_record(rec, out);
}

// Forward declarations: defined after helper functions below
static fwpb_status sign_raw_tx_with_hw_key(void);
static void stream_precompute_signatures(void);

static fwpb_status map_psbt_sign_result(key_manager_psbt_sign_result_t result) {
  switch (result) {
    case KEY_MANAGER_PSBT_SIGN_OK:
      return fwpb_status_SUCCESS;
    case KEY_MANAGER_PSBT_SIGN_INVALID_PARAM:
      return fwpb_status_INVALID_ARGUMENT;
    case KEY_MANAGER_PSBT_SIGN_KEYPATH_MISMATCH:
    case KEY_MANAGER_PSBT_SIGN_DERIVATION_FAILED:
      return fwpb_status_KEY_DERIVATION_FAILED;
    case KEY_MANAGER_PSBT_SIGN_CRYPTO_BUSY:
      return fwpb_status_IN_PROGRESS;
    case KEY_MANAGER_PSBT_SIGN_SIGNING_FAILED:
      return fwpb_status_SIGNING_FAILED;
    default:
      return fwpb_status_ERROR;
  }
}

NO_OPTIMIZE void key_manager_task_try_deferred_sign(void) {
  // Guard: stream_session shares the same union memory as signing_session.
  // signing_session.active (bool, value 1) aliases stream_session.state and
  // reads as STREAM_STATE_RECEIVING.  Only skip if the stream session is in a
  // state that indicates an actual streaming flow (FINALIZED or CONFIRMED);
  // STREAM_STATE_RECEIVING (== 1) could be the signing_session.active alias.
  if (stream_session.state == STREAM_STATE_FINALIZED ||
      stream_session.state == STREAM_STATE_CONFIRMED) {
    return;
  }
  if (!signing_session.active || signing_session.num_signatures > 0) {
    return;
  }
  if (confirmation_manager_get_type() != CONFIRMATION_TYPE_SIGN_TRANSACTION) {
    return;
  }
  if (!confirmation_manager_is_approved()) {
    LOGD("Dfrd: no approval");
    return;
  }

  // Verify the session commitment BEFORE signing.
  //
  // Recompute the hash from the live signing_session and compare it against
  // the commitment stored at display time.  This is the authoritative check:
  // sign_transaction_confirmation_result_handler() also re-verifies as a
  // secondary SECURE_IF_FAILIN defence, but signing must not proceed unless
  // the session is still bit-for-bit identical to what the user approved.
  tx_session_confirmation_data_t confirmed_data = {0};
  size_t confirmed_data_size = 0;
  if (!confirmation_manager_get_operation_data(CONFIRMATION_TYPE_SIGN_TRANSACTION, &confirmed_data,
                                               &confirmed_data_size) ||
      confirmed_data_size != sizeof(tx_session_confirmation_data_t)) {
    LOGE("Dfrd: no conf data");
    signing_session.sign_result = fwpb_status_ERROR;
    memzero(&confirmed_data, sizeof(confirmed_data));
    return;
  }

  uint8_t current_hash[SHA256_DIGEST_SIZE] = {0};
  if (!compute_session_commitment_hash(&signing_session, current_hash)) {
    LOGE("Dfrd: hash fail");
    signing_session.sign_result = fwpb_status_ERROR;
    memzero(&confirmed_data, sizeof(confirmed_data));
    return;
  }

  SECURE_IF_FAILIN(memcmp_s(current_hash, confirmed_data.session_hash, SHA256_DIGEST_SIZE) != 0) {
    LOGE("Dfrd: commit mismatch");
    signing_session.sign_result = fwpb_status_VERIFICATION_FAILED;
    memzero(current_hash, sizeof(current_hash));
    memzero(&confirmed_data, sizeof(confirmed_data));
    return;
  }

  memzero(current_hash, sizeof(current_hash));
  memzero(&confirmed_data, sizeof(confirmed_data));

  signing_session.sign_result = sign_raw_tx_with_hw_key();
}

NO_OPTIMIZE void key_manager_task_try_deferred_stream_sign(void) {
  if (stream_session.state != STREAM_STATE_FINALIZED) {
    return;
  }
  if (confirmation_manager_get_type() != CONFIRMATION_TYPE_SIGN_TRANSACTION) {
    return;
  }
  if (!confirmation_manager_is_approved()) {
    return;
  }

  // ---------------------------------------------------------------------------
  // Session commitment verification (streaming path)
  //
  // Before precomputing signatures, recompute the commitment hash from live
  // session fields and verify it matches what was stored at confirmation time.
  // This provides the same post-approval substitution defense as non-streaming.
  // ---------------------------------------------------------------------------
  stream_session_confirmation_data_t confirmed_data = {0};
  size_t confirmed_data_size = 0;
  if (!confirmation_manager_get_operation_data(CONFIRMATION_TYPE_SIGN_TRANSACTION, &confirmed_data,
                                               &confirmed_data_size) ||
      confirmed_data_size != sizeof(stream_session_confirmation_data_t)) {
    LOGE("SDfrd: no conf data");
    // Terminal failure: clear state so next get_confirmation_result returns error.
    confirmation_manager_clear();
    stream_session_reset();
    return;
  }

  // Re-read flash and recompute payload hash to detect post-approval corruption.
  // This is critical because per-input amounts are read from flash at signing time.
  uint8_t current_payload_hash[SHA256_DIGEST_SIZE] = {0};
  if (!recompute_payload_hash_from_flash(current_payload_hash)) {
    LOGE("SDfrd: flash hash fail");
    memzero(&confirmed_data, sizeof(confirmed_data));
    confirmation_manager_clear();
    stream_session_reset();
    return;
  }

  // Recompute commitment hash from live session state + payload hash and compare.
  uint8_t current_hash[SHA256_DIGEST_SIZE] = {0};
  if (!compute_stream_session_commitment_hash(&stream_session, current_payload_hash,
                                              current_hash)) {
    LOGE("SDfrd: hash fail");
    memzero(current_payload_hash, sizeof(current_payload_hash));
    memzero(&confirmed_data, sizeof(confirmed_data));
    confirmation_manager_clear();
    stream_session_reset();
    return;
  }
  memzero(current_payload_hash, sizeof(current_payload_hash));

  // Uses SECURE_IF_FAILIN to guard against fault injection attacks.
  SECURE_IF_FAILIN(memcmp_s(current_hash, confirmed_data.session_hash, SHA256_DIGEST_SIZE) != 0) {
    LOGE("SDfrd: commit mismatch");
    memzero(current_hash, sizeof(current_hash));
    memzero(&confirmed_data, sizeof(confirmed_data));
    // Terminal failure: clear state so next get_confirmation_result returns error.
    confirmation_manager_clear();
    stream_session_reset();
    return;
  }

  memzero(current_hash, sizeof(current_hash));
  memzero(&confirmed_data, sizeof(confirmed_data));

  // Transition to CONFIRMED and pre-compute all signatures.
  // The "Signing..." screen is already shown by the money_movement flow's
  // approve handler (navigates to FLOW_CONFIRMATION before we get here).
  // This runs on the key_manager task stack (8 KiB), which is large
  // enough for the ECDSA signing calls inside stream_precompute_signatures.
  stream_session.state = STREAM_STATE_CONFIRMED;
  stream_precompute_signatures();
}

static void sap_clear_state(void) {
  confirmation_manager_clear();
  sap_session_init(&sap_session);
}

void key_manager_task_try_sap_deferred_sign(void) {
  if (!sap_session.pending_data.valid || sap_session.signed_ok) {
    return;
  }
  if (confirmation_manager_get_type() != CONFIRMATION_TYPE_SIGN_ACTION_PROOF) {
    return;
  }
  if (!confirmation_manager_is_approved()) {
    return;
  }
  sap_sign(&sap_session);
}

static bool sap_confirmation_result_handler(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  confirmation_result_t validation =
    confirmation_manager_validate(cmd->msg.get_confirmation_result_cmd.response_handle.bytes,
                                  cmd->msg.get_confirmation_result_cmd.response_handle.size,
                                  cmd->msg.get_confirmation_result_cmd.confirmation_handle.bytes,
                                  cmd->msg.get_confirmation_result_cmd.confirmation_handle.size);

  rsp->which_msg = fwpb_wallet_rsp_get_confirmation_result_rsp_tag;

  if (validation == CONFIRMATION_RESULT_NOT_APPROVED) {
    rsp->status = fwpb_status_CONFIRMATION_PENDING;
    proto_send_rsp(cmd, rsp);
    return true;
  }

  if (validation != CONFIRMATION_RESULT_SUCCESS) {
    rsp->status = fwpb_status_CONFIRMATION_NOT_COMPLETED;
    LOGE("SAP confirm fail: %d", validation);
    sap_clear_state();
    proto_send_rsp(cmd, rsp);
    return false;
  }

  if (sap_session.signed_ok) {
    rsp->msg.get_confirmation_result_rsp.which_result =
      fwpb_get_confirmation_result_rsp_sign_action_proof_result_tag;
    uint8_t* sig_out =
      rsp->msg.get_confirmation_result_rsp.result.sign_action_proof_result.signature.bytes;
    memcpy(sig_out, sap_session.signature, SAP_SIG_SIZE);
    rsp->msg.get_confirmation_result_rsp.result.sign_action_proof_result.signature.size =
      SAP_SIG_SIZE;
    rsp->status = fwpb_status_SUCCESS;
    sap_clear_state();
    proto_send_rsp(cmd, rsp);
    return true;
  }

  if (sap_session.sign_attempted) {
    // Signing was attempted but failed
    rsp->status = (fwpb_status)sap_session.sign_result;
    sap_clear_state();
    proto_send_rsp(cmd, rsp);
    return false;
  }

  // Deferred sign hasn't run yet — tell caller to keep polling
  rsp->status = fwpb_status_CONFIRMATION_PENDING;
  proto_send_rsp(cmd, rsp);
  return true;
}

static bool sign_transaction_confirmation_result_handler(ipc_ref_t* message);

// Forward declarations for helpers used by precompute
static bool derive_child_pubkey_from_xpub(const xpub_t* account_xpub, const uint32_t* full_path,
                                          size_t full_path_len, size_t account_depth,
                                          uint8_t* pubkey_out);
static fwpb_status map_psbt_sign_result(key_manager_psbt_sign_result_t result);

// ---------------------------------------------------------------------------
// Pre-compute all streaming signatures to flash after user confirmation.
// Loads keyset once, signs each input, writes fixed-size records to
// STREAM_SIGS_PATH. Called synchronously in the key_manager task — the
// NFC command queue is idle until the user's second tap arrives.
// ---------------------------------------------------------------------------
static void stream_precompute_signatures(void) {
  // Precompute starting

  // Create (or truncate) the sigs file
  {
    fs_file_t* file = NULL;
    if (fs_open_global(&file, STREAM_SIGS_PATH, FS_O_WRONLY | FS_O_CREAT | FS_O_TRUNC) != 0) {
      LOGE("PC: file create");
      stream_session.precompute_failed = true;
      return;
    }
    fs_close_global(file);
  }

  // Load keyset once for all inputs
  wallet_keyset_t keyset = {0};
  if (!wkek_read_and_decrypt(WALLET_KEYSET_PATH, (uint8_t*)&keyset, sizeof(keyset))) {
    LOGE("PC: KS load");
    stream_session.precompute_failed = true;
    return;
  }
  if (keyset.version != WALLET_KEYSET_VERSION) {
    LOGE("PC: KS ver %d", keyset.version);
    memzero(&keyset, sizeof(keyset));
    stream_session.precompute_failed = true;
    return;
  }

  const size_t app_hw_account_depth = 3;
  const size_t server_account_depth = 0;

  // See sign_raw_tx_with_hw_key for rationale: normal path rejects any
  // non-current-account input, sweep path uses OLD-account xpubs.
  const bool is_sweep = stream_session.sweep.active;
  const uint32_t expected_account_index =
    is_sweep ? stream_session.sweep.old_account_index : (uint32_t)keyset.account_index;
  const xpub_t* app_xpub_src = is_sweep ? &stream_session.sweep.app_xpub : &keyset.app;
  const xpub_t* hw_xpub_src = is_sweep ? &stream_session.sweep.hw_xpub : &keyset.hw;
  const xpub_t* server_xpub_src = is_sweep ? &stream_session.sweep.server_xpub : &keyset.server;

  // Only one file handle may be open at a time (fs_open_global uses a single
  // global handle).  The loop alternates between reading the payload file and
  // writing the sigs file, opening/closing each per iteration.

  uint32_t t_start = rtos_thread_systime();

  for (uint32_t i = 0; i < stream_session.num_inputs; i++) {
    // --- Read phase: open payload file, parse input, close ---
    raw_tx_input_t input = {0};
    if (!parse_input_from_flash(i, &input)) {
      LOGE("PC: parse %lu", (unsigned long)i);
      goto fail;
    }

    // Account-consistency check (see sign_raw_tx_with_hw_key).
    {
      uint32_t input_account = input.derivation_path[2] & ~0x80000000u;
      if (input_account != expected_account_index) {
        LOGE("PC: in %lu acct %lu != %lu", (unsigned long)i, (unsigned long)input_account,
             (unsigned long)expected_account_index);
        goto fail;
      }
    }

    // Derive child pubkeys
    uint8_t child_pubkeys[PSBT_P2WSH_MAX_KEYPATHS * PSBT_P2WSH_PUBKEY_LEN];
    const xpub_t* xpubs[PSBT_P2WSH_MAX_KEYPATHS] = {app_xpub_src, hw_xpub_src, server_xpub_src};
    const size_t account_depths[PSBT_P2WSH_MAX_KEYPATHS] = {
      app_hw_account_depth, app_hw_account_depth, server_account_depth};

    uint32_t server_path[PSBT_BIP32_PATH_MAX_LEN];
    size_t server_path_len = input.derivation_path_len;
    if (server_path_len > PSBT_BIP32_PATH_MAX_LEN) {
      LOGE("PC: path %lu", (unsigned long)i);
      goto fail;
    }
    for (size_t j = 0; j < server_path_len; j++) {
      server_path[j] = input.derivation_path[j] & ~0x80000000u;
    }
    server_path[2] = 0;  // server account is always 0

    for (size_t k = 0; k < PSBT_P2WSH_MAX_KEYPATHS; k++) {
      const uint32_t* path = (k == KEY_INDEX_SERVER) ? server_path : input.derivation_path;
      size_t path_len = (k == KEY_INDEX_SERVER) ? server_path_len : input.derivation_path_len;

      if (!derive_child_pubkey_from_xpub(xpubs[k], path, path_len, account_depths[k],
                                         &child_pubkeys[k * PSBT_P2WSH_PUBKEY_LEN])) {
        LOGE("PC: derive %lu/%zu", (unsigned long)i, k);
        goto fail;
      }
    }

    // Compute sighash
    psbt_p2wsh_signing_data_t signing_data = {0};
    psbt_error_t err = raw_tx_p2wsh_input_signing_data_precomputed(
      stream_session.hash_prevouts, stream_session.hash_sequence, stream_session.hash_outputs,
      stream_session.lock_time, stream_session.version, &input, child_pubkeys, &signing_data);
    if (err != PSBT_OK) {
      LOGE("PC: sdata %lu:%d", (unsigned long)i, err);
      goto fail;
    }

    // ECDSA sign
    key_manager_psbt_input_t km_input = {
      .input_index = i,
      .signing_data = signing_data,
    };
    key_manager_psbt_signature_t sig = {0};
    size_t sigs_written = 0;
    key_manager_psbt_sign_result_t sign_result =
      key_manager_psbt_sign_p2wsh_inputs(&km_input, 1, &sig, 1, &sigs_written);
    if (sign_result != KEY_MANAGER_PSBT_SIGN_OK || sigs_written != 1) {
      LOGE("PC: sign %lu:%d", (unsigned long)i, sign_result);
      goto fail;
    }

    // --- Write phase: open sigs file, append record, close ---
    uint8_t record[SIG_RECORD_SIZE];
    memset(record, 0, sizeof(record));
    memcpy(record, sig.pubkey, PSBT_P2WSH_PUBKEY_LEN);
    record[SIG_RECORD_PUBKEY_SIZE] = (uint8_t)sig.signature_len;
    memcpy(record + SIG_RECORD_PUBKEY_SIZE + SIG_RECORD_SIGLEN_SIZE, sig.signature,
           sig.signature_len);

    {
      fs_file_t* sigs_file = NULL;
      if (fs_open_global(&sigs_file, STREAM_SIGS_PATH, FS_O_WRONLY | FS_O_APPEND) != 0) {
        LOGE("PC: file open %lu", (unsigned long)i);
        goto fail;
      }
      int32_t written = fs_file_write(sigs_file, record, sizeof(record));
      fs_close_global(sigs_file);
      if (written != (int32_t)sizeof(record)) {
        LOGE("PC: write %lu:%ld", (unsigned long)i, (long)written);
        goto fail;
      }
    }

    stream_session.sigs_computed = i + 1;
  }

  uint32_t elapsed = rtos_thread_systime() - t_start;
  memzero(&keyset, sizeof(keyset));
  stream_session.precompute_done = true;
  LOGD("PC: %lu in %lums", (unsigned long)stream_session.sigs_computed, (unsigned long)elapsed);
  return;

fail:
  memzero(&keyset, sizeof(keyset));
  stream_session.precompute_failed = true;
  // Remove partial sigs file to reclaim flash — the fallback path computes
  // on demand and doesn't use the cache.
  fs_remove(STREAM_SIGS_PATH);
  stream_session.sigs_computed = 0;
  LOGE("PC: partial write fail");
}

// ---------------------------------------------------------------------------
// Init command handler: lost_app_recovery_cmd
// ---------------------------------------------------------------------------
void key_manager_task_handle_lost_app_recovery(ipc_ref_t* message) {
  lost_app_recovery_handle_init(message);
}

// ---------------------------------------------------------------------------
// Init command handler: rotate_app_auth_keys_cmd
// ---------------------------------------------------------------------------
void key_manager_task_handle_rotate_app_auth_keys(ipc_ref_t* message) {
  rotate_app_auth_keys_handle_init(message);
}

// ---------------------------------------------------------------------------
// Init command handler: upgrade_rotate_app_auth_keys_cmd
// ---------------------------------------------------------------------------
void key_manager_task_handle_upgrade_rotate_app_auth_keys(ipc_ref_t* message) {
  upgrade_rotate_app_auth_keys_handle_init(message);
}

// ---------------------------------------------------------------------------
// Continue command handler: lost_app_recovery_continue_cmd
// ---------------------------------------------------------------------------
void key_manager_task_handle_lost_app_recovery_continue(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_lost_app_recovery_continue_rsp_tag;
  rsp->status = fwpb_status_ERROR;

  if (!lost_app_recovery_is_session_ready()) {
    goto out;
  }

  derivation_path_t* auth_path = wallet_get_w1_auth_path();

  // 1. Sign ActionProof payload with HW auth key, reusing shared SAP logic.
  if (cmd->msg.lost_app_recovery_continue_cmd.action_proof_version != 1) {
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }
  sap_action_t action = sap_parse_action(cmd->msg.lost_app_recovery_continue_cmd.action);
  if (action == SAP_ACTION_COUNT) {
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }
  // Only CreateLostAppRecovery is permitted — that's the action the user
  // explicitly confirmed on the hardware display.
  if (action != SAP_ACTION_CREATE_LOST_APP_RECOVERY) {
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }
  {
    sap_session_t lar_sap = {0};
    sap_session_init(&lar_sap);
    lar_sap.pending_data.valid = true;
    lar_sap.pending_data.version = cmd->msg.lost_app_recovery_continue_cmd.action_proof_version;

    // Reject inputs that would be truncated by strncpy (fields are C strings, not byte buffers).
    if (strlen(cmd->msg.lost_app_recovery_continue_cmd.action) >=
          sizeof(lar_sap.pending_data.action) ||
        strlen(cmd->msg.lost_app_recovery_continue_cmd.value) >=
          sizeof(lar_sap.pending_data.value) ||
        strlen(cmd->msg.lost_app_recovery_continue_cmd.bindings) >=
          sizeof(lar_sap.pending_data.bindings)) {
      rsp->status = fwpb_status_INVALID_ARGUMENT;
      goto out;
    }

    strncpy(lar_sap.pending_data.action, cmd->msg.lost_app_recovery_continue_cmd.action,
            sizeof(lar_sap.pending_data.action) - 1);
    strncpy(lar_sap.pending_data.value, cmd->msg.lost_app_recovery_continue_cmd.value,
            sizeof(lar_sap.pending_data.value) - 1);
    strncpy(lar_sap.pending_data.bindings, cmd->msg.lost_app_recovery_continue_cmd.bindings,
            sizeof(lar_sap.pending_data.bindings) - 1);

    int sign_status = sap_sign(&lar_sap);
    if (sign_status != fwpb_status_SUCCESS) {
      rsp->status = sign_status;
      goto out;
    }
    memcpy(rsp->msg.lost_app_recovery_continue_rsp.action_proof_signature.bytes, lar_sap.signature,
           ECC_SIG_SIZE);
  }
  rsp->msg.lost_app_recovery_continue_rsp.action_proof_signature.size = ECC_SIG_SIZE;

  // 2. Derive next spending key and return descriptor metadata (same contract as handle_derive).
  {
    bool is_mainnet = (cmd->msg.lost_app_recovery_continue_cmd.network == fwpb_btc_network_BITCOIN);
    uint32_t next_index = cmd->msg.lost_app_recovery_continue_cmd.next_account_index;

    version_bytes_t version = is_mainnet ? MAINNET_PUB : TESTNET_PUB;
    uint32_t spending_indices[] = {
      BIP84_PURPOSE | BIP32_HARDENED_BIT,
      (is_mainnet ? BIP32_COIN_BTC : BIP32_COIN_TESTNET) | BIP32_HARDENED_BIT,
      next_index | BIP32_HARDENED_BIT,
    };
    derivation_path_t spending_path = {
      .indices = spending_indices,
      .num_indices = BIP32_PATH_DEPTH_ACCOUNT,
    };

    if (!key_manager_derive_key_descriptor(
          spending_path, version,
          &rsp->msg.lost_app_recovery_continue_rsp.spending_key_descriptor)) {
      rsp->status = fwpb_status_KEY_DERIVATION_FAILED;
      goto out;
    }
    rsp->msg.lost_app_recovery_continue_rsp.has_spending_key_descriptor = true;
    memcpy(rsp->msg.lost_app_recovery_continue_rsp.bare_spending_key.bytes,
           rsp->msg.lost_app_recovery_continue_rsp.spending_key_descriptor.bare_bip32_key.bytes,
           BIP32_SERIALIZED_EXT_KEY_SIZE);
    rsp->msg.lost_app_recovery_continue_rsp.bare_spending_key.size = BIP32_SERIALIZED_EXT_KEY_SIZE;
  }

  // 3. Sign app_global_auth_key with domain separation:
  //    SHA-256("BKRelationshipEndorsement" || app_global_auth_key_bytes)
  {
    uint8_t hash[SHA256_DIGEST_SIZE] = {0};
#define LAR_KEY_DOMAIN_TAG "BKRelationshipEndorsement"
    hash_stream_ctx_t ctx;
    if (!crypto_sha256_stream_init(&ctx) ||
        !crypto_sha256_stream_update(&ctx, (uint8_t*)LAR_KEY_DOMAIN_TAG,
                                     sizeof(LAR_KEY_DOMAIN_TAG) - 1) ||
        !crypto_sha256_stream_update(
          &ctx, (uint8_t*)cmd->msg.lost_app_recovery_continue_cmd.app_global_auth_key.bytes,
          cmd->msg.lost_app_recovery_continue_cmd.app_global_auth_key.size) ||
        !crypto_sha256_stream_final(&ctx, hash)) {
      goto out;
    }
#undef LAR_KEY_DOMAIN_TAG
    key_manager_sign_result_t sign_res = key_manager_derive_and_sign(
      *auth_path, hash, rsp->msg.lost_app_recovery_continue_rsp.app_auth_key_signature.bytes);
    memzero(hash, sizeof(hash));
    switch (sign_res) {
      case KEY_MANAGER_SIGN_SUCCESS:
        break;
      case KEY_MANAGER_SIGN_DERIVATION_FAILED:
        rsp->status = fwpb_status_KEY_DERIVATION_FAILED;
        goto out;
      case KEY_MANAGER_SIGN_POLICY_VIOLATION:
        rsp->status = fwpb_status_INVALID_ARGUMENT;
        goto out;
      default:
        rsp->status = fwpb_status_SIGNING_FAILED;
        goto out;
    }
  }
  rsp->msg.lost_app_recovery_continue_rsp.app_auth_key_signature.size = ECC_SIG_SIZE;

  rsp->status = fwpb_status_SUCCESS;

out:
  if (rsp->status != fwpb_status_SUCCESS) {
    LOGE("LAR continue: status %d", rsp->status);
  } else {
    ui_show_confirmation("Success", true);
  }
  lost_app_recovery_clear_session();
  confirmation_manager_clear();
  proto_send_rsp(cmd, rsp);
}

// ---------------------------------------------------------------------------
// Sign challenge handler: lost_app_recovery_sign_challenge_cmd
// ---------------------------------------------------------------------------
void key_manager_task_handle_lost_app_recovery_sign_challenge(ipc_ref_t* message) {
  lost_app_recovery_sign_challenge_handle_init(message);
}

// ---------------------------------------------------------------------------
// Recovery composite command handlers
// ---------------------------------------------------------------------------
void key_manager_task_handle_sign_challenge_and_seal_seks(ipc_ref_t* message) {
  recovery_composites_sign_challenge_and_seal_handle_init(message);
}

void key_manager_task_handle_recovery_authorize_lost_app(ipc_ref_t* message) {
  recovery_composites_authorize_lost_app_handle_init(message);
}

void key_manager_task_handle_recovery_authorize_lost_hw(ipc_ref_t* message) {
  recovery_composites_authorize_lost_hw_handle_init(message);
}

void key_manager_task_handle_upgrade_authorize_w3(ipc_ref_t* message) {
  recovery_composites_upgrade_authorize_w3_handle_init(message);
}

// ---------------------------------------------------------------------------
// EEK restoration unseal symmetric key handler: eek_restoration_unseal_symmetric_key_cmd
// ---------------------------------------------------------------------------
void key_manager_task_handle_eek_restoration_unseal_symmetric_key(ipc_ref_t* message) {
  eek_restoration_unseal_symmetric_key_handle_init(message);
}

// ---------------------------------------------------------------------------
// Full account cloud backup restoration handler: full_account_cloud_backup_restoration_cmd
// ---------------------------------------------------------------------------
void key_manager_task_handle_full_account_cloud_backup_restoration(ipc_ref_t* message) {
  full_account_cloud_backup_restoration_handle_init(message);
}

// ---------------------------------------------------------------------------
// Full account cloud backup restoration continue handler
// ---------------------------------------------------------------------------
void key_manager_task_handle_full_account_cloud_backup_restoration_continue(ipc_ref_t* message) {
  full_account_cloud_backup_restoration_handle_continue(message);
}

void key_manager_task_register_listeners(void) {
  uc_route_register(fwpb_uxc_msg_device_secure_channel_response_tag,
                    _key_manager_task_handle_uxc_session_response, NULL);

  confirmation_manager_register_result_handler(CONFIRMATION_TYPE_SIGN_TRANSACTION,
                                               sign_transaction_confirmation_result_handler);
  confirmation_manager_register_result_handler(CONFIRMATION_TYPE_SIGN_ACTION_PROOF,
                                               sap_confirmation_result_handler);

  // Lost app recovery confirmation handler (implemented in lost_app_recovery.c)
  lost_app_recovery_register_handlers();

  // Rotate app auth keys confirmation handler (implemented in rotate_app_auth_keys.c)
  rotate_app_auth_keys_register_handlers();

  // Upgrade rotate app auth keys confirmation handler (implemented in
  // upgrade_rotate_app_auth_keys.c)
  upgrade_rotate_app_auth_keys_register_handlers();

  // Recovery composite command handlers (implemented in recovery_composites.c)
  recovery_composites_register_handlers();
  eek_restoration_register_handlers();
  full_account_cloud_backup_restoration_register_handlers();
}

// ---------------------------------------------------------------------------
// Non-PSBT signing: sign_tx_request handler
// ---------------------------------------------------------------------------

// Derive a child pubkey from an xpub (account-level) using the non-hardened
// suffix of the derivation path. The full path is e.g. [84', 0', 0', 0, 7],
// and the account xpub is at depth 3, so we derive using the last 2 components [0, 7].
static bool derive_child_pubkey_from_xpub(const xpub_t* account_xpub, const uint32_t* full_path,
                                          size_t full_path_len, size_t account_depth,
                                          uint8_t pubkey_out[PSBT_P2WSH_PUBKEY_LEN]) {
  if (full_path_len <= account_depth) {
    return false;
  }

  extended_key_t parent_pub = {0};
  parent_pub.prefix = account_xpub->pubkey[0];
  memcpy(parent_pub.key, &account_xpub->pubkey[1], BIP32_KEY_SIZE);
  memcpy(parent_pub.chaincode, account_xpub->chaincode, BIP32_CHAINCODE_SIZE);

  derivation_path_t child_path = {
    .indices = (uint32_t*)&full_path[account_depth],
    .num_indices = full_path_len - account_depth,
  };

  extended_key_t child_pub = {0};
  if (!bip32_derive_path_pub(&parent_pub, &child_pub, &child_path)) {
    return false;
  }

  pubkey_out[0] = child_pub.prefix;
  memcpy(&pubkey_out[1], child_pub.key, BIP32_KEY_SIZE);
  return true;
}

static fwpb_status sign_raw_tx_with_hw_key(void) {
  wallet_keyset_t keyset = {0};
  if (!wkek_read_and_decrypt(WALLET_KEYSET_PATH, (uint8_t*)&keyset, sizeof(keyset))) {
    LOGE("Sign: KS load");
    return fwpb_status_DESCRIPTOR_NOT_LOADED;
  }

  if (keyset.version != WALLET_KEYSET_VERSION) {
    LOGE("Sign: KS ver %d", keyset.version);
    memzero(&keyset, sizeof(keyset));
    return fwpb_status_ERROR;
  }

  // Account-consistency guard.
  //
  // Normal signing (signing_session.sweep.active == false): every input must
  // reference keyset.account_index. Any non-current-account input is rejected
  // so a compromised app cannot smuggle an old-account spend through the
  // regular path. Sweeps must go through sweep_sign_cmd.
  //
  // Sweep signing (signing_session.sweep.active == true): every input must
  // reference signing_session.sweep.old_account_index (validated at request
  // time but re-checked here defensively).
  const bool is_sweep = signing_session.sweep.active;
  const uint32_t expected_account_index =
    is_sweep ? signing_session.sweep.old_account_index : (uint32_t)keyset.account_index;
  for (size_t i = 0; i < signing_session.num_inputs; i++) {
    uint32_t input_account = signing_session.inputs[i].derivation_path[2] & ~0x80000000u;
    if (input_account != expected_account_index) {
      LOGE("Sign: in %zu acct %lu != %lu", i, (unsigned long)input_account,
           (unsigned long)expected_account_index);
      memzero(&keyset, sizeof(keyset));
      return fwpb_status_INVALID_ARGUMENT;
    }
  }

  // App and HW xpubs are at account depth 3: m/84'/coin'/account'
  // Server xpub is at depth 0 (all non-hardened derivation via chaincode delegation).
  // For app/hw: derive child using the non-hardened suffix (path[3:], e.g. [0, 0])
  // For server: derive child using the full path with hardened bits stripped
  //             (e.g. [84', 1', 0', 0, 0] → [84, 1, 0, 0, 0])
  const size_t app_hw_account_depth = 3;
  const size_t server_account_depth = 0;

  // In sweep mode, substitute OLD-account xpubs: app + server come from the
  // sweep_sign_cmd, HW is derived from master (see validate_and_store_sweep_ctx).
  const xpub_t* app_xpub_src = is_sweep ? &signing_session.sweep.app_xpub : &keyset.app;
  const xpub_t* hw_xpub_src = is_sweep ? &signing_session.sweep.hw_xpub : &keyset.hw;
  const xpub_t* server_xpub_src = is_sweep ? &signing_session.sweep.server_xpub : &keyset.server;

  signing_session.num_signatures = 0;

  for (size_t i = 0; i < signing_session.num_inputs; i++) {
    const raw_tx_input_t* input = &signing_session.inputs[i];

    // Derive child pubkeys from xpubs for this input's derivation path.
    // App/HW use account_depth=3, server uses account_depth=0 with hardened bits stripped.
    uint8_t child_pubkeys[PSBT_P2WSH_MAX_KEYPATHS * PSBT_P2WSH_PUBKEY_LEN];
    const xpub_t* xpubs[PSBT_P2WSH_MAX_KEYPATHS] = {app_xpub_src, hw_xpub_src, server_xpub_src};
    const size_t account_depths[PSBT_P2WSH_MAX_KEYPATHS] = {
      app_hw_account_depth, app_hw_account_depth, server_account_depth};

    // Build the server's non-hardened path by stripping hardened bits
    uint32_t server_path[PSBT_BIP32_PATH_MAX_LEN];
    size_t server_path_len = input->derivation_path_len;
    if (server_path_len > PSBT_BIP32_PATH_MAX_LEN) {
      LOGE("Sign: srv path %zu", server_path_len);
      memzero(&keyset, sizeof(keyset));
      return fwpb_status_ERROR;
    }
    for (size_t j = 0; j < server_path_len; j++) {
      server_path[j] = input->derivation_path[j] & ~0x80000000u;  // strip hardened bit
    }
    server_path[2] = 0;  // server account is always 0

    for (size_t k = 0; k < PSBT_P2WSH_MAX_KEYPATHS; k++) {
      // Server key uses the non-hardened path; app/hw use the original (hardened) path
      const uint32_t* path = (k == KEY_INDEX_SERVER) ? server_path : input->derivation_path;
      size_t path_len = (k == KEY_INDEX_SERVER) ? server_path_len : input->derivation_path_len;

      if (!derive_child_pubkey_from_xpub(xpubs[k], path, path_len, account_depths[k],
                                         &child_pubkeys[k * PSBT_P2WSH_PUBKEY_LEN])) {
        LOGE("Sign: derive %zu/%zu", i, k);
        memzero(&keyset, sizeof(keyset));
        return fwpb_status_ERROR;
      }
    }

    // Compute signing data (witness script, sighash) from raw tx fields
    psbt_p2wsh_signing_data_t signing_data = {0};
    psbt_error_t err = raw_tx_p2wsh_input_signing_data(
      signing_session.inputs, signing_session.num_inputs, signing_session.outputs,
      signing_session.num_outputs, signing_session.lock_time, signing_session.version, i,
      child_pubkeys, &signing_data);

    if (err != PSBT_OK) {
      LOGE("Sign: sdata %zu:%d", i, err);
      memzero(&keyset, sizeof(keyset));
      return fwpb_status_ERROR;
    }

    // Build key_manager_psbt_input_t for the existing signing infrastructure
    key_manager_psbt_input_t km_input = {
      .input_index = i,
      .signing_data = signing_data,
    };

    key_manager_psbt_signature_t sig = {0};
    size_t sigs_written = 0;
    key_manager_psbt_sign_result_t sign_result =
      key_manager_psbt_sign_p2wsh_inputs(&km_input, 1, &sig, 1, &sigs_written);

    if (sign_result != KEY_MANAGER_PSBT_SIGN_OK || sigs_written != 1) {
      LOGE("Sign: fail %zu:%d", i, sign_result);
      memzero(&keyset, sizeof(keyset));
      return map_psbt_sign_result(sign_result);
    }

    signing_session.signatures[signing_session.num_signatures] = sig;
    signing_session.num_signatures++;
  }

  memzero(&keyset, sizeof(keyset));
  return fwpb_status_SUCCESS;
}

static NO_OPTIMIZE bool sign_transaction_confirmation_result_handler(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  // Validate handles
  confirmation_result_t validation =
    confirmation_manager_validate(cmd->msg.get_confirmation_result_cmd.response_handle.bytes,
                                  cmd->msg.get_confirmation_result_cmd.response_handle.size,
                                  cmd->msg.get_confirmation_result_cmd.confirmation_handle.bytes,
                                  cmd->msg.get_confirmation_result_cmd.confirmation_handle.size);

  rsp->which_msg = fwpb_wallet_rsp_get_confirmation_result_rsp_tag;

  if (validation == CONFIRMATION_RESULT_NOT_APPROVED) {
    rsp->status = fwpb_status_CONFIRMATION_PENDING;
    proto_send_rsp(cmd, rsp);
    return true;
  }

  if (validation != CONFIRMATION_RESULT_SUCCESS) {
    rsp->status = fwpb_status_CONFIRMATION_NOT_COMPLETED;
    LOGE("Raw sign confirm fail: %d", validation);
    proto_send_rsp(cmd, rsp);
    return false;
  }

  // ---------------------------------------------------------------------------
  // Streaming signing path: check BEFORE non-streaming session commitment.
  //
  // Streaming signing stores stream_session_confirmation_data_t in the
  // confirmation manager.  Recompute the commitment hash from live session
  // fields and verify it matches what was stored at confirmation time.
  // ---------------------------------------------------------------------------
  if (stream_session.state == STREAM_STATE_CONFIRMED) {
    // Verify streaming session commitment before returning signatures.
    stream_session_confirmation_data_t stream_confirmed = {0};
    size_t stream_confirmed_size = 0;
    if (!confirmation_manager_get_operation_data(CONFIRMATION_TYPE_SIGN_TRANSACTION,
                                                 &stream_confirmed, &stream_confirmed_size) ||
        stream_confirmed_size != sizeof(stream_session_confirmation_data_t)) {
      LOGE("SSC: no conf data");
      rsp->status = fwpb_status_ERROR;
      confirmation_manager_clear();
      stream_session_reset();
      proto_send_rsp(cmd, rsp);
      return false;
    }

    // Re-read flash and recompute payload hash to detect post-approval corruption.
    uint8_t current_payload_hash[SHA256_DIGEST_SIZE] = {0};
    if (!recompute_payload_hash_from_flash(current_payload_hash)) {
      LOGE("SSC: flash hash fail");
      memzero(&stream_confirmed, sizeof(stream_confirmed));
      rsp->status = fwpb_status_ERROR;
      confirmation_manager_clear();
      stream_session_reset();
      proto_send_rsp(cmd, rsp);
      return false;
    }

    // Recompute commitment hash from live session state + payload hash and compare.
    uint8_t stream_hash[SHA256_DIGEST_SIZE] = {0};
    if (!compute_stream_session_commitment_hash(&stream_session, current_payload_hash,
                                                stream_hash)) {
      LOGE("SSC: hash fail");
      memzero(current_payload_hash, sizeof(current_payload_hash));
      memzero(&stream_confirmed, sizeof(stream_confirmed));
      rsp->status = fwpb_status_ERROR;
      confirmation_manager_clear();
      stream_session_reset();
      proto_send_rsp(cmd, rsp);
      return false;
    }
    memzero(current_payload_hash, sizeof(current_payload_hash));

    // Uses SECURE_IF_FAILIN to guard against fault injection attacks.
    SECURE_IF_FAILIN(memcmp_s(stream_hash, stream_confirmed.session_hash, SHA256_DIGEST_SIZE) !=
                     0) {
      LOGE("SSC: mismatch");
      memzero(stream_hash, sizeof(stream_hash));
      memzero(&stream_confirmed, sizeof(stream_confirmed));
      rsp->status = fwpb_status_VERIFICATION_FAILED;
      confirmation_manager_clear();
      stream_session_reset();
      proto_send_rsp(cmd, rsp);
      return false;
    }

    memzero(stream_hash, sizeof(stream_hash));
    memzero(&stream_confirmed, sizeof(stream_confirmed));

    rsp->msg.get_confirmation_result_rsp.which_result =
      fwpb_get_confirmation_result_rsp_sign_stream_signatures_ready_tag;
    rsp->msg.get_confirmation_result_rsp.result.sign_stream_signatures_ready.num_inputs =
      stream_session.num_inputs;
    rsp->status = fwpb_status_SUCCESS;
    proto_send_rsp(cmd, rsp);
    return true;
  }

  // Still FINALIZED — deferred precompute hasn't run yet; tell app to retry.
  if (stream_session.state == STREAM_STATE_FINALIZED) {
    rsp->status = fwpb_status_CONFIRMATION_PENDING;
    proto_send_rsp(cmd, rsp);
    return true;
  }

  // ---------------------------------------------------------------------------
  // Non-streaming session commitment verification
  //
  // Retrieve the tx_session_confirmation_data_t that was stored when the user
  // was shown the transaction for approval.  Recompute the session hash from
  // the live signing_session and compare it against the confirmed hash.
  //
  // This guarantees that the fields being signed are bit-for-bit identical to
  // what the user approved on screen.  A mismatch (due to corruption, a race,
  // or an adversarial substitution) causes an immediate rejection.
  // ---------------------------------------------------------------------------
  tx_session_confirmation_data_t confirmed_data = {0};
  size_t confirmed_data_size = 0;
  if (!confirmation_manager_get_operation_data(CONFIRMATION_TYPE_SIGN_TRANSACTION, &confirmed_data,
                                               &confirmed_data_size) ||
      confirmed_data_size != sizeof(tx_session_confirmation_data_t)) {
    LOGE("SC: no conf data");
    rsp->status = fwpb_status_ERROR;
    confirmation_manager_clear();
    signing_session_reset();
    proto_send_rsp(cmd, rsp);
    return false;
  }

  uint8_t current_hash[SHA256_DIGEST_SIZE] = {0};
  if (!compute_session_commitment_hash(&signing_session, current_hash)) {
    LOGE("SC: hash fail");
    rsp->status = fwpb_status_ERROR;
    confirmation_manager_clear();
    signing_session_reset();
    proto_send_rsp(cmd, rsp);
    return false;
  }

  // Constant-time comparison guarded with SECURE_IF so a glitch cannot bypass
  // the check and allow signing of a substituted transaction.
  SECURE_IF_FAILIN(memcmp_s(current_hash, confirmed_data.session_hash, SHA256_DIGEST_SIZE) != 0) {
    LOGE("SC: mismatch");
    memzero(current_hash, sizeof(current_hash));
    memzero(&confirmed_data, sizeof(confirmed_data));
    rsp->status = fwpb_status_VERIFICATION_FAILED;
    confirmation_manager_clear();
    signing_session_reset();
    proto_send_rsp(cmd, rsp);
    return false;
  }

  memzero(current_hash, sizeof(current_hash));
  memzero(&confirmed_data, sizeof(confirmed_data));

  // Non-streaming path: check if signing completed (all inputs must be signed)
  if (signing_session.num_signatures == signing_session.num_inputs &&
      signing_session.num_signatures > 0) {
    // Return signatures directly in the response (no chunking needed)
    rsp->msg.get_confirmation_result_rsp.which_result =
      fwpb_get_confirmation_result_rsp_sign_tx_result_tag;

    fwpb_sign_tx_response* tx_rsp = &rsp->msg.get_confirmation_result_rsp.result.sign_tx_result;
    tx_rsp->signatures_count = signing_session.num_signatures;

    for (size_t i = 0; i < signing_session.num_signatures; i++) {
      const key_manager_psbt_signature_t* sig = &signing_session.signatures[i];
      tx_rsp->signatures[i].input_index = sig->input_index;
      memcpy(tx_rsp->signatures[i].public_key.bytes, sig->pubkey, PSBT_P2WSH_PUBKEY_LEN);
      tx_rsp->signatures[i].public_key.size = PSBT_P2WSH_PUBKEY_LEN;
      memcpy(tx_rsp->signatures[i].signature.bytes, sig->signature, sig->signature_len);
      tx_rsp->signatures[i].signature.size = sig->signature_len;
    }

    rsp->status = fwpb_status_SUCCESS;
    confirmation_manager_clear();
    signing_session_reset();
    ui_show_confirmation("Success", true);
    proto_send_rsp(cmd, rsp);
    return true;
  }

  // Check if signing failed
  if (signing_session.sign_result != fwpb_status_SUCCESS && signing_session.sign_result != 0) {
    rsp->status = signing_session.sign_result;
    LOGE("Raw sign fail post-confirm");
    confirmation_manager_clear();
    signing_session_reset();
    proto_send_rsp(cmd, rsp);
    return false;
  }

  rsp->status = fwpb_status_CONFIRMATION_PENDING;
  proto_send_rsp(cmd, rsp);
  return true;
}

static fwpb_status raw_tx_request_confirmation(fwpb_wallet_rsp* rsp, uint32_t btc_display_unit) {
  wallet_keyset_t keyset = {0};
  if (!wkek_read_and_decrypt(WALLET_KEYSET_PATH, (uint8_t*)&keyset, sizeof(keyset))) {
    LOGE("Keyset load fail");
    return fwpb_status_DESCRIPTOR_NOT_LOADED;
  }

  ew_network_t network =
    (keyset.network == NETWORK_MAINNET) ? EW_NETWORK_MAINNET : EW_NETWORK_TESTNET;

  // Build the confirmation data: display summary + session commitment hash.
  // The hash is computed over all canonical tx fields so that the confirmation
  // manager holds a cryptographic commitment to exactly what was displayed.
  tx_session_confirmation_data_t confirmation_data = {0};

  if (raw_tx_get_info(signing_session.inputs, signing_session.num_inputs, signing_session.outputs,
                      signing_session.num_outputs, network,
                      &confirmation_data.display_info) != PSBT_OK) {
    memzero(&keyset, sizeof(keyset));
    return fwpb_status_ERROR;
  }

  // Validate change outputs: prove each output flagged as "change" (has_derivation_path=true)
  // actually belongs to our wallet policy by deriving the expected P2WSH scriptPubKey from the
  // stored keyset and comparing it against the output's destination_spk. Without this check, a
  // malicious app could label an attacker-controlled output as change and hide it from the user.
  for (size_t i = 0; i < signing_session.num_outputs; i++) {
    const raw_tx_output_t* output = &signing_session.outputs[i];
    if (!output->has_derivation_path) {
      continue;
    }
    if (!wallet_change_output_belongs_to_policy(
          &keyset, output->derivation_path, output->derivation_path_len, output->destination_spk,
          output->destination_spk_len)) {
      LOGE("Chg out %zu policy mismatch", i);
      memzero(&keyset, sizeof(keyset));
      return fwpb_status_ERROR;
    }
  }

  memzero(&keyset, sizeof(keyset));

  if (!compute_session_commitment_hash(&signing_session, confirmation_data.session_hash)) {
    LOGE("SC: hash fail");
    return fwpb_status_ERROR;
  }

  uint8_t response_handle[CONFIRMATION_HANDLE_SIZE], confirmation_handle[CONFIRMATION_HANDLE_SIZE];
  if (confirmation_manager_create(CONFIRMATION_TYPE_SIGN_TRANSACTION, &confirmation_data,
                                  sizeof(tx_session_confirmation_data_t), response_handle,
                                  sizeof(response_handle), confirmation_handle,
                                  sizeof(confirmation_handle)) != CONFIRMATION_RESULT_SUCCESS) {
    return fwpb_status_ERROR;
  }

  // Convenience alias for display formatting below.
  const psbt_info_t* tx_info = &confirmation_data.display_info;

  send_transaction_data_t tx_display = {0};

  // Detect self-send (sweep/consolidation): all outputs belong to the wallet.
  bool is_self_send = !tx_info->has_destination;
  tx_display.flow = is_self_send ? fwpb_money_movement_flow_MONEY_MOVEMENT_FLOW_SELF_SEND
                                 : fwpb_money_movement_flow_MONEY_MOVEMENT_FLOW_SEND;

  // For self-send, display change amount (funds staying in wallet); otherwise send amount.
  uint64_t display_amount = is_self_send ? tx_info->change_amount_sats : tx_info->send_amount_sats;
  int ret =
    snprintf(tx_display.amount_sats, sizeof(tx_display.amount_sats), "%llu", display_amount);
  if (ret < 0 || ret >= (int)sizeof(tx_display.amount_sats)) {
    confirmation_manager_clear();
    return fwpb_status_ERROR;
  }

  ret =
    snprintf(tx_display.fee_sats, sizeof(tx_display.fee_sats), "%llu", tx_info->fee_amount_sats);
  if (ret < 0 || ret >= (int)sizeof(tx_display.fee_sats)) {
    confirmation_manager_clear();
    return fwpb_status_ERROR;
  }

  if (!is_self_send) {
    strncpy(tx_display.address, tx_info->destination_address, sizeof(tx_display.address) - 1);
    tx_display.address[sizeof(tx_display.address) - 1] = '\0';
  }

  // Populate display preferences (ephemeral, from signing command)
  tx_display.btc_display_unit = btc_display_unit;

  UI_SHOW_EVENT_WITH_DATA(UI_EVENT_START_SEND_TRANSACTION, &tx_display,
                          sizeof(send_transaction_data_t));

  rsp->status = fwpb_status_CONFIRMATION_PENDING;
  memcpy(rsp->response_handle.bytes, response_handle, sizeof(response_handle));
  rsp->response_handle.size = sizeof(response_handle);
  memcpy(rsp->confirmation_handle.bytes, confirmation_handle, sizeof(confirmation_handle));
  rsp->confirmation_handle.size = sizeof(confirmation_handle);
  return fwpb_status_SUCCESS;
}

void key_manager_task_handle_sign_tx_request(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_sign_tx_response_tag;

  // Unconditionally tear down any prior session (transaction, streaming, or SAP)
  confirmation_manager_clear();
  signing_session_reset();
  stream_session_reset();
  sap_session_init(&sap_session);

  const fwpb_sign_tx_request_cmd* req = &cmd->msg.sign_tx_request_cmd;

  // Validate input/output counts
  if (req->inputs_count == 0 || req->inputs_count > RAW_TX_MAX_INPUTS || req->outputs_count == 0 ||
      req->outputs_count > RAW_TX_MAX_OUTPUTS) {
    LOGE("Tx: %lu in %lu out", (unsigned long)req->inputs_count, (unsigned long)req->outputs_count);
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }

  // Validate and store transaction version (only version 1 and 2 are standard)
  if (req->version == 0 || req->version > 2) {
    LOGE("Tx: bad ver %lu", (unsigned long)req->version);
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }

  // Parse inputs from proto
  signing_session.num_inputs = req->inputs_count;
  signing_session.lock_time = req->lock_time;
  signing_session.version = req->version;
  for (size_t i = 0; i < req->inputs_count; i++) {
    const fwpb_sign_tx_input* proto_input = &req->inputs[i];
    raw_tx_input_t* input = &signing_session.inputs[i];

    if (proto_input->prev_txid.size != 32) {
      LOGE("Tx: txid sz %zu", i);
      rsp->status = fwpb_status_INVALID_ARGUMENT;
      signing_session_reset();
      goto out;
    }

    memcpy(input->prev_txid, proto_input->prev_txid.bytes, 32);
    input->prev_index = proto_input->prev_index;
    input->sequence = proto_input->sequence;
    input->amount = proto_input->amount;

    // Validate derivation path: BIP84 multisig requires at least 5 components
    // (m/84'/coin'/acct'/change/index) and at most PSBT_BIP32_PATH_MAX_LEN.
    if (proto_input->derivation_path_count < 5 ||
        proto_input->derivation_path_count > PSBT_BIP32_PATH_MAX_LEN) {
      LOGE("Tx: path len %zu:%lu", i, (unsigned long)proto_input->derivation_path_count);
      rsp->status = fwpb_status_INVALID_ARGUMENT;
      signing_session_reset();
      goto out;
    }
    input->derivation_path_len = proto_input->derivation_path_count;
    for (size_t j = 0; j < proto_input->derivation_path_count; j++) {
      input->derivation_path[j] = proto_input->derivation_path[j];
    }
  }

  // Parse outputs from proto
  signing_session.num_outputs = req->outputs_count;
  for (size_t i = 0; i < req->outputs_count; i++) {
    const fwpb_sign_tx_output* proto_output = &req->outputs[i];
    raw_tx_output_t* output = &signing_session.outputs[i];

    if (proto_output->destination_spk.size == 0 ||
        proto_output->destination_spk.size > sizeof(output->destination_spk)) {
      LOGE("Tx: spk sz %zu", i);
      rsp->status = fwpb_status_INVALID_ARGUMENT;
      signing_session_reset();
      goto out;
    }

    output->amount = proto_output->amount;
    memcpy(output->destination_spk, proto_output->destination_spk.bytes,
           proto_output->destination_spk.size);
    output->destination_spk_len = proto_output->destination_spk.size;
    output->has_derivation_path = proto_output->has_derivation_path;
    if (proto_output->has_derivation_path) {
      if (proto_output->derivation_path_count == 0 ||
          proto_output->derivation_path_count > PSBT_BIP32_PATH_MAX_LEN) {
        LOGE("Tx: opath len %zu", i);
        rsp->status = fwpb_status_INVALID_ARGUMENT;
        signing_session_reset();
        goto out;
      }
      output->derivation_path_len = proto_output->derivation_path_count;
      for (size_t j = 0; j < proto_output->derivation_path_count; j++) {
        output->derivation_path[j] = proto_output->derivation_path[j];
      }
    } else {
      if (proto_output->derivation_path_count != 0) {
        LOGE("Tx: unexpected path %zu", i);
        rsp->status = fwpb_status_INVALID_ARGUMENT;
        signing_session_reset();
        goto out;
      }
      output->derivation_path_len = 0;
    }
  }

  signing_session.active = true;

  // Request user confirmation (displays tx details on screen)
  fwpb_status confirm_status = raw_tx_request_confirmation(rsp, (uint32_t)req->btc_display_unit);
  if (confirm_status != fwpb_status_SUCCESS) {
    rsp->status = confirm_status;
    signing_session_reset();
  }

out:
  proto_send_rsp(cmd, rsp);
}

// ---------------------------------------------------------------------------
// Sweep signing helpers and handlers
// ---------------------------------------------------------------------------

// Derive the HW account-level xpub from master at m/84'/coin'/old_account'.
// Called once per sweep request to populate sweep_context_t.hw_xpub; subsequent
// per-input derivation reuses this via derive_child_pubkey_from_xpub just like
// the normal signing path does with keyset.hw.
static bool derive_sweep_hw_xpub(uint32_t old_account_index, uint8_t network, xpub_t* xpub_out) {
  uint32_t indices[] = {
    BIP84_PURPOSE | BIP32_HARDENED_BIT,
    (network == NETWORK_MAINNET ? BIP32_COIN_BTC : BIP32_COIN_TESTNET) | BIP32_HARDENED_BIT,
    old_account_index | BIP32_HARDENED_BIT};
  derivation_path_t path = {.indices = indices, .num_indices = BIP32_PATH_DEPTH_ACCOUNT};

  extended_key_t priv __attribute__((__cleanup__(bip32_zero_key)));
  if (!wallet_derive_key_priv_using_cache(&priv, path)) {
    return false;
  }
  extended_key_t pub __attribute__((__cleanup__(bip32_zero_key)));
  if (!bip32_priv_to_pub(&priv, &pub)) {
    return false;
  }
  xpub_out->pubkey[0] = pub.prefix;
  memcpy(&xpub_out->pubkey[1], pub.key, BIP32_KEY_SIZE);
  memcpy(xpub_out->chaincode, pub.chaincode, BIP32_CHAINCODE_SIZE);
  return true;
}

// Validate sweep-command-level invariants and populate a sweep_context_t.
// Enforces:
//   - old_account_index differs from the on-device keyset.account_index
//   - app + server xpubs have the right on-wire sizes and are valid points
//   - HW xpub can be derived from master at the requested account index
static fwpb_status validate_and_init_sweep_ctx(
  const wallet_keyset_t* keyset, uint32_t old_account_index, const uint8_t* app_pubkey,
  size_t app_pubkey_size, const uint8_t* app_chaincode, size_t app_chaincode_size,
  const uint8_t* server_pubkey, size_t server_pubkey_size, const uint8_t* server_chaincode,
  size_t server_chaincode_size, sweep_context_t* out) {
  if (old_account_index == (uint32_t)keyset->account_index) {
    LOGE("SwS: acct == cur (%lu)", (unsigned long)old_account_index);
    return fwpb_status_INVALID_ARGUMENT;
  }
  if (app_pubkey_size != PUBKEY_LENGTH || app_chaincode_size != CHAINCODE_LENGTH ||
      server_pubkey_size != PUBKEY_LENGTH || server_chaincode_size != CHAINCODE_LENGTH) {
    LOGE("SwS: xpub sz");
    return fwpb_status_INVALID_ARGUMENT;
  }
  if (!validate_pubkey(app_pubkey) || !validate_pubkey(server_pubkey)) {
    LOGE("SwS: bad pk");
    return fwpb_status_INVALID_ARGUMENT;
  }

  memset(out, 0, sizeof(*out));
  memcpy(out->app_xpub.pubkey, app_pubkey, PUBKEY_LENGTH);
  memcpy(out->app_xpub.chaincode, app_chaincode, CHAINCODE_LENGTH);
  memcpy(out->server_xpub.pubkey, server_pubkey, PUBKEY_LENGTH);
  memcpy(out->server_xpub.chaincode, server_chaincode, CHAINCODE_LENGTH);

  if (!derive_sweep_hw_xpub(old_account_index, keyset->network, &out->hw_xpub)) {
    LOGE("SwS: hw derive");
    return fwpb_status_ERROR;
  }

  out->old_account_index = old_account_index;
  out->active = true;
  return fwpb_status_SUCCESS;
}

// Derive the expected P2WSH scriptPubKey for the CURRENT keyset's fresh
// receive address (m/84'/coin'/current_account'/0/0). Populates
// [spk_out] / [spk_len_out] on success. Used by the sweep path to verify
// that any non-derivation-path ("external") output actually lands on the
// user's own fresh receive address rather than an attacker-controlled one.
static bool derive_current_fresh_receive_spk(const wallet_keyset_t* keyset, uint8_t* spk_out,
                                             size_t spk_buf_len, size_t* spk_len_out) {
  uint32_t path[] = {
    BIP84_PURPOSE | BIP32_HARDENED_BIT,
    (keyset->network == NETWORK_MAINNET ? BIP32_COIN_BTC : BIP32_COIN_TESTNET) | BIP32_HARDENED_BIT,
    ((uint32_t)keyset->account_index) | BIP32_HARDENED_BIT,
    0u,
    0u,
  };
  return wallet_derive_p2wsh_scriptpubkey(keyset, path, sizeof(path) / sizeof(path[0]), spk_out,
                                          spk_buf_len, spk_len_out) == WALLET_RES_OK;
}

// Validate sweep-specific tx shape for the one-shot signing path.
//
// A sweep is structurally a single-output transaction: all old-account UTXOs
// move to exactly ONE fresh receive address on the current keyset at
// m/84'/coin'/current'/0/0. This tight shape is easier to audit than a
// multi-output disjunction and removes the theoretical case where a crafted
// request could ride multiple outputs that each individually satisfy the
// sweep invariant.
//
// Invariants:
//  - every input references `old_account_index`
//  - num_outputs == 1
//  - that single output's scriptPubKey exactly matches the firmware-derived
//    P2WSH scriptPubKey at current-keyset /0/0. This works for both external
//    destinations (no bip32_derivation on the output) AND derivation-path
//    destinations — the scriptPubKey match is cryptographic and sufficient
//    on its own, so no separate `has_derivation_path` branching is needed.
static fwpb_status validate_sweep_tx_shape(const wallet_keyset_t* keyset,
                                           uint32_t old_account_index, const raw_tx_input_t* inputs,
                                           size_t num_inputs, const raw_tx_output_t* outputs,
                                           size_t num_outputs) {
  for (size_t i = 0; i < num_inputs; i++) {
    uint32_t acct = inputs[i].derivation_path[2] & ~0x80000000u;
    if (acct != old_account_index) {
      LOGE("SwS: in %zu acct %lu != %lu", i, (unsigned long)acct, (unsigned long)old_account_index);
      return fwpb_status_INVALID_ARGUMENT;
    }
  }

  if (num_outputs != 1) {
    LOGE("SwS: expected 1 output, got %zu", num_outputs);
    return fwpb_status_INVALID_ARGUMENT;
  }

  uint8_t fresh_spk[sizeof(outputs[0].destination_spk)];
  size_t fresh_spk_len = 0;
  if (!derive_current_fresh_receive_spk(keyset, fresh_spk, sizeof(fresh_spk), &fresh_spk_len)) {
    LOGE("SwS: fresh spk derive");
    return fwpb_status_ERROR;
  }

  const raw_tx_output_t* out = &outputs[0];
  if (out->destination_spk_len != fresh_spk_len ||
      memcmp(out->destination_spk, fresh_spk, fresh_spk_len) != 0) {
    LOGE("SwS: out spk mismatch (not current /0/0)");
    return fwpb_status_INVALID_ARGUMENT;
  }
  return fwpb_status_SUCCESS;
}

void key_manager_task_handle_sweep_sign(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_sweep_sign_rsp_tag;

  // Unconditionally tear down any prior session (transaction, streaming, or SAP)
  confirmation_manager_clear();
  signing_session_reset();
  stream_session_reset();
  sap_session_init(&sap_session);

  const fwpb_sweep_sign_cmd* req = &cmd->msg.sweep_sign_cmd;

  if (req->inputs_count == 0 || req->inputs_count > RAW_TX_MAX_INPUTS || req->outputs_count == 0 ||
      req->outputs_count > RAW_TX_MAX_OUTPUTS) {
    LOGE("SwS: %lu in %lu out", (unsigned long)req->inputs_count,
         (unsigned long)req->outputs_count);
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }
  if (req->version == 0 || req->version > 2) {
    LOGE("SwS: bad ver %lu", (unsigned long)req->version);
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }

  wallet_keyset_t keyset = {0};
  if (!wkek_read_and_decrypt(WALLET_KEYSET_PATH, (uint8_t*)&keyset, sizeof(keyset))) {
    LOGE("SwS: KS load");
    rsp->status = fwpb_status_DESCRIPTOR_NOT_LOADED;
    goto out;
  }
  if (keyset.version != WALLET_KEYSET_VERSION) {
    LOGE("SwS: KS ver %d", keyset.version);
    memzero(&keyset, sizeof(keyset));
    rsp->status = fwpb_status_ERROR;
    goto out;
  }

  fwpb_status sweep_status = validate_and_init_sweep_ctx(
    &keyset, req->old_account_index, req->sweep_app_xpub_pubkey.bytes,
    req->sweep_app_xpub_pubkey.size, req->sweep_app_xpub_chaincode.bytes,
    req->sweep_app_xpub_chaincode.size, req->sweep_server_xpub_pubkey.bytes,
    req->sweep_server_xpub_pubkey.size, req->sweep_server_xpub_chaincode.bytes,
    req->sweep_server_xpub_chaincode.size, &signing_session.sweep);
  if (sweep_status != fwpb_status_SUCCESS) {
    memzero(&keyset, sizeof(keyset));
    signing_session_reset();
    rsp->status = sweep_status;
    goto out;
  }

  // Parse inputs/outputs using the same logic as handle_sign_tx_request.
  signing_session.num_inputs = req->inputs_count;
  signing_session.lock_time = req->lock_time;
  signing_session.version = req->version;
  for (size_t i = 0; i < req->inputs_count; i++) {
    const fwpb_sign_tx_input* proto_input = &req->inputs[i];
    raw_tx_input_t* input = &signing_session.inputs[i];

    if (proto_input->prev_txid.size != 32) {
      LOGE("SwS: txid sz %zu", i);
      rsp->status = fwpb_status_INVALID_ARGUMENT;
      memzero(&keyset, sizeof(keyset));
      signing_session_reset();
      goto out;
    }
    memcpy(input->prev_txid, proto_input->prev_txid.bytes, 32);
    input->prev_index = proto_input->prev_index;
    input->sequence = proto_input->sequence;
    input->amount = proto_input->amount;

    if (proto_input->derivation_path_count < 5 ||
        proto_input->derivation_path_count > PSBT_BIP32_PATH_MAX_LEN) {
      LOGE("SwS: path len %zu:%lu", i, (unsigned long)proto_input->derivation_path_count);
      rsp->status = fwpb_status_INVALID_ARGUMENT;
      memzero(&keyset, sizeof(keyset));
      signing_session_reset();
      goto out;
    }
    input->derivation_path_len = proto_input->derivation_path_count;
    for (size_t j = 0; j < proto_input->derivation_path_count; j++) {
      input->derivation_path[j] = proto_input->derivation_path[j];
    }
  }

  signing_session.num_outputs = req->outputs_count;
  for (size_t i = 0; i < req->outputs_count; i++) {
    const fwpb_sign_tx_output* proto_output = &req->outputs[i];
    raw_tx_output_t* output = &signing_session.outputs[i];

    if (proto_output->destination_spk.size == 0 ||
        proto_output->destination_spk.size > sizeof(output->destination_spk)) {
      LOGE("SwS: spk sz %zu", i);
      rsp->status = fwpb_status_INVALID_ARGUMENT;
      memzero(&keyset, sizeof(keyset));
      signing_session_reset();
      goto out;
    }
    output->amount = proto_output->amount;
    memcpy(output->destination_spk, proto_output->destination_spk.bytes,
           proto_output->destination_spk.size);
    output->destination_spk_len = proto_output->destination_spk.size;
    output->has_derivation_path = proto_output->has_derivation_path;
    if (proto_output->has_derivation_path) {
      if (proto_output->derivation_path_count == 0 ||
          proto_output->derivation_path_count > PSBT_BIP32_PATH_MAX_LEN) {
        LOGE("SwS: opath len %zu", i);
        rsp->status = fwpb_status_INVALID_ARGUMENT;
        memzero(&keyset, sizeof(keyset));
        signing_session_reset();
        goto out;
      }
      output->derivation_path_len = proto_output->derivation_path_count;
      for (size_t j = 0; j < proto_output->derivation_path_count; j++) {
        output->derivation_path[j] = proto_output->derivation_path[j];
      }
    } else {
      if (proto_output->derivation_path_count != 0) {
        LOGE("SwS: unexpected path %zu", i);
        rsp->status = fwpb_status_INVALID_ARGUMENT;
        memzero(&keyset, sizeof(keyset));
        signing_session_reset();
        goto out;
      }
      output->derivation_path_len = 0;
    }
  }

  fwpb_status shape_status = validate_sweep_tx_shape(
    &keyset, signing_session.sweep.old_account_index, signing_session.inputs,
    signing_session.num_inputs, signing_session.outputs, signing_session.num_outputs);
  if (shape_status != fwpb_status_SUCCESS) {
    memzero(&keyset, sizeof(keyset));
    signing_session_reset();
    rsp->status = shape_status;
    goto out;
  }

  memzero(&keyset, sizeof(keyset));
  signing_session.active = true;

  // Reuse the same confirmation flow as normal signing.
  // raw_tx_request_confirmation internally verifies every derivation-path
  // output belongs to the CURRENT keyset's P2WSH policy — which is exactly
  // what a sweep needs (destinations go to the current account).
  fwpb_status confirm_status = raw_tx_request_confirmation(rsp, (uint32_t)req->btc_display_unit);
  if (confirm_status != fwpb_status_SUCCESS) {
    rsp->status = confirm_status;
    signing_session_reset();
  }

out:
  proto_send_rsp(cmd, rsp);
}

void key_manager_task_handle_sweep_sign_stream_start(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_sweep_sign_stream_start_rsp_tag;

  confirmation_manager_clear();
  signing_session_reset();
  stream_session_reset();
  sap_session_init(&sap_session);
  fwup_cleanup_stale_patch();

  const fwpb_sweep_sign_stream_start_cmd* req = &cmd->msg.sweep_sign_stream_start_cmd;

  if (req->num_inputs == 0 || req->num_outputs == 0) {
    LOGE("SwSS: zero in/out");
    rsp->msg.sweep_sign_stream_start_rsp.rsp_status =
      fwpb_sweep_sign_stream_start_rsp_sweep_sign_stream_start_rsp_status_ERROR;
    goto out;
  }
  if (req->num_inputs > STREAM_TX_MAX_INPUTS || req->num_outputs > STREAM_TX_MAX_OUTPUTS) {
    LOGE("SwSS: max in=%lu out=%lu", (unsigned long)req->num_inputs,
         (unsigned long)req->num_outputs);
    rsp->msg.sweep_sign_stream_start_rsp.rsp_status =
      fwpb_sweep_sign_stream_start_rsp_sweep_sign_stream_start_rsp_status_ERROR;
    goto out;
  }
  if (req->version == 0 || req->version > 2) {
    LOGE("SwSS: ver %lu", (unsigned long)req->version);
    rsp->msg.sweep_sign_stream_start_rsp.rsp_status =
      fwpb_sweep_sign_stream_start_rsp_sweep_sign_stream_start_rsp_status_ERROR;
    goto out;
  }

  uint32_t expected_size = STREAM_HEADER_SIZE + req->num_inputs * STREAM_INPUT_RECORD_SIZE +
                           req->num_outputs * STREAM_OUTPUT_RECORD_SIZE;
  if (req->payload_size != expected_size) {
    LOGE("SwSS: sz %lu!=%lu", (unsigned long)req->payload_size, (unsigned long)expected_size);
    rsp->msg.sweep_sign_stream_start_rsp.rsp_status =
      fwpb_sweep_sign_stream_start_rsp_sweep_sign_stream_start_rsp_status_ERROR;
    goto out;
  }

  wallet_keyset_t keyset = {0};
  if (!wkek_read_and_decrypt(WALLET_KEYSET_PATH, (uint8_t*)&keyset, sizeof(keyset))) {
    LOGE("SwSS: KS load");
    rsp->msg.sweep_sign_stream_start_rsp.rsp_status =
      fwpb_sweep_sign_stream_start_rsp_sweep_sign_stream_start_rsp_status_ERROR;
    goto out;
  }
  if (keyset.version != WALLET_KEYSET_VERSION) {
    LOGE("SwSS: KS ver %d", keyset.version);
    memzero(&keyset, sizeof(keyset));
    rsp->msg.sweep_sign_stream_start_rsp.rsp_status =
      fwpb_sweep_sign_stream_start_rsp_sweep_sign_stream_start_rsp_status_ERROR;
    goto out;
  }

  fwpb_status sweep_status = validate_and_init_sweep_ctx(
    &keyset, req->old_account_index, req->sweep_app_xpub_pubkey.bytes,
    req->sweep_app_xpub_pubkey.size, req->sweep_app_xpub_chaincode.bytes,
    req->sweep_app_xpub_chaincode.size, req->sweep_server_xpub_pubkey.bytes,
    req->sweep_server_xpub_pubkey.size, req->sweep_server_xpub_chaincode.bytes,
    req->sweep_server_xpub_chaincode.size, &stream_session.sweep);
  memzero(&keyset, sizeof(keyset));
  if (sweep_status != fwpb_status_SUCCESS) {
    stream_session_reset();
    rsp->msg.sweep_sign_stream_start_rsp.rsp_status =
      (sweep_status == fwpb_status_UNAUTHENTICATED)
        ? fwpb_sweep_sign_stream_start_rsp_sweep_sign_stream_start_rsp_status_UNAUTHENTICATED
        : fwpb_sweep_sign_stream_start_rsp_sweep_sign_stream_start_rsp_status_ERROR;
    goto out;
  }

  // Create (or truncate) the flash file for payload storage
  {
    fs_file_t* file = NULL;
    if (fs_open_global(&file, STREAM_PAYLOAD_PATH, FS_O_WRONLY | FS_O_CREAT | FS_O_TRUNC) != 0) {
      LOGE("SwSS: file create");
      stream_session_reset();
      rsp->msg.sweep_sign_stream_start_rsp.rsp_status =
        fwpb_sweep_sign_stream_start_rsp_sweep_sign_stream_start_rsp_status_ERROR;
      goto out;
    }
    fs_close_global(file);
  }

  stream_session.state = STREAM_STATE_RECEIVING;
  stream_session.num_inputs = req->num_inputs;
  stream_session.num_outputs = req->num_outputs;
  stream_session.version = req->version;
  stream_session.lock_time = req->lock_time;
  stream_session.expected_payload_size = req->payload_size;
  stream_session.bytes_received = 0;
  stream_session.next_sequence_id = 0;
  stream_session.btc_display_unit = (uint32_t)req->btc_display_unit;

  if (!crypto_sha256_stream_init(&stream_session.commitment_ctx)) {
    LOGE("SwSS: sha init");
    stream_session_reset();
    rsp->msg.sweep_sign_stream_start_rsp.rsp_status =
      fwpb_sweep_sign_stream_start_rsp_sweep_sign_stream_start_rsp_status_ERROR;
    goto out;
  }

  rsp->msg.sweep_sign_stream_start_rsp.rsp_status =
    fwpb_sweep_sign_stream_start_rsp_sweep_sign_stream_start_rsp_status_SUCCESS;

out:
  proto_send_rsp(cmd, rsp);
}

void key_manager_task_handle_sign_action_proof(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_sign_action_proof_rsp_tag;
  rsp->status = fwpb_status_ERROR;

  // Unconditionally tear down any prior session (transaction, streaming, or SAP)
  confirmation_manager_clear();
  signing_session_reset();
  stream_session_reset();
  sap_session_init(&sap_session);

  if (cmd->msg.sign_action_proof_cmd.version != 1) {
    LOGE("SAP bad version: %lu", (unsigned long)cmd->msg.sign_action_proof_cmd.version);
    goto out;
  }

  sap_action_t action = sap_parse_action(cmd->msg.sign_action_proof_cmd.action);
  if (action == SAP_ACTION_COUNT) {
    LOGE("SAP: bad action");
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }

  // Early check: verify auth key is available before showing UI (sap_sign re-derives later)
  extended_key_t auth_key __attribute__((__cleanup__(bip32_zero_key)));
  if (!wallet_get_w1_auth_key(&auth_key)) {
    LOGE("SAP: no auth key");
    rsp->status = fwpb_status_KEY_DERIVATION_FAILED;
    goto out;
  }

  sap_session.pending_data.version = cmd->msg.sign_action_proof_cmd.version;
  strncpy(sap_session.pending_data.action, cmd->msg.sign_action_proof_cmd.action,
          sizeof(sap_session.pending_data.action) - 1);
  strncpy(sap_session.pending_data.value, cmd->msg.sign_action_proof_cmd.value,
          sizeof(sap_session.pending_data.value) - 1);
  strncpy(sap_session.pending_data.bindings, cmd->msg.sign_action_proof_cmd.bindings,
          sizeof(sap_session.pending_data.bindings) - 1);
  sap_session.pending_data.valid = true;

  uint8_t response_handle[CONFIRMATION_HANDLE_SIZE];
  uint8_t confirmation_handle[CONFIRMATION_HANDLE_SIZE];
  // SAP doesn't carry operation-specific data, use a placeholder token
  uint8_t sap_cm_token = 1;
  if (confirmation_manager_create(CONFIRMATION_TYPE_SIGN_ACTION_PROOF, &sap_cm_token,
                                  sizeof(sap_cm_token), response_handle, sizeof(response_handle),
                                  confirmation_handle,
                                  sizeof(confirmation_handle)) != CONFIRMATION_RESULT_SUCCESS) {
    LOGE("SAP: CM fail");
    sap_session_init(&sap_session);
    rsp->status = fwpb_status_ERROR;
    goto out;
  }

  fwpb_display_params_privileged_action display_params = {0};
  display_params.sap_action = (uint32_t)action;

  if (strlen(cmd->msg.sign_action_proof_cmd.value) > 0) {
    display_params.which_action = fwpb_display_params_privileged_action_confirm_string_tag;
    strncpy(display_params.action.confirm_string.value, cmd->msg.sign_action_proof_cmd.value,
            sizeof(display_params.action.confirm_string.value) - 1);
  } else {
    display_params.which_action = fwpb_display_params_privileged_action_confirm_action_tag;
    display_params.action.confirm_action.action_type =
      fwpb_display_privileged_action_type_DISPLAY_PRIVILEGED_ACTION_NONE;
  }

  UI_SHOW_EVENT_WITH_DATA(UI_EVENT_START_PRIVILEGED_ACTION, &display_params,
                          sizeof(display_params));

  rsp->status = fwpb_status_CONFIRMATION_PENDING;
  memcpy(rsp->response_handle.bytes, response_handle, sizeof(response_handle));
  rsp->response_handle.size = sizeof(response_handle);
  memcpy(rsp->confirmation_handle.bytes, confirmation_handle, sizeof(confirmation_handle));
  rsp->confirmation_handle.size = sizeof(confirmation_handle);

out:
  proto_send_rsp(cmd, rsp);
}

// ---------------------------------------------------------------------------
// Streaming signing: compute BIP143 intermediate hashes from flash payload
// Opens the flash file once for all reads.
// ---------------------------------------------------------------------------
static bool stream_compute_bip143_hashes(void) {
  fs_file_t* file = NULL;
  if (fs_open_global(&file, STREAM_PAYLOAD_PATH, FS_O_RDONLY) != 0)
    return false;

  bool ok = false;
  hash_stream_ctx_t ctx;
  uint8_t inner[SHA256_DIGEST_SIZE];

  // hashPrevouts = hash256(concat of all outpoints: prev_txid(32) || prev_index(4LE))
  if (!crypto_sha256_stream_init(&ctx))
    goto out;
  for (uint32_t i = 0; i < stream_session.num_inputs; i++) {
    uint8_t outpoint[36];  // prev_txid(32) + prev_index(4)
    uint32_t offset = STREAM_HEADER_SIZE + i * STREAM_INPUT_RECORD_SIZE;
    if (!flash_read_at_f(file, offset, outpoint, sizeof(outpoint)))
      goto out;
    if (!crypto_sha256_stream_update(&ctx, outpoint, sizeof(outpoint)))
      goto out;
  }
  if (!crypto_sha256_stream_final(&ctx, inner))
    goto out;
  if (!crypto_hash(inner, SHA256_DIGEST_SIZE, stream_session.hash_prevouts, SHA256_DIGEST_SIZE,
                   ALG_SHA256))
    goto out;

  // hashSequence = hash256(concat of all sequences: seq(4LE))
  if (!crypto_sha256_stream_init(&ctx))
    goto out;
  for (uint32_t i = 0; i < stream_session.num_inputs; i++) {
    uint8_t seq[4];
    uint32_t offset = STREAM_HEADER_SIZE + i * STREAM_INPUT_RECORD_SIZE + 36;
    if (!flash_read_at_f(file, offset, seq, sizeof(seq)))
      goto out;
    if (!crypto_sha256_stream_update(&ctx, seq, sizeof(seq)))
      goto out;
  }
  if (!crypto_sha256_stream_final(&ctx, inner))
    goto out;
  if (!crypto_hash(inner, SHA256_DIGEST_SIZE, stream_session.hash_sequence, SHA256_DIGEST_SIZE,
                   ALG_SHA256))
    goto out;

  // hashOutputs = hash256(concat of all outputs: amount(8LE) || varint(spk_len) || spk)
  if (!crypto_sha256_stream_init(&ctx))
    goto out;
  for (uint32_t i = 0; i < stream_session.num_outputs; i++) {
    raw_tx_output_t parsed_out = {0};
    if (!parse_output_from_flash_f(file, i, &parsed_out))
      goto out;
    uint8_t buf[8];
    for (int b = 0; b < 8; b++) buf[b] = (uint8_t)(parsed_out.amount >> (b * 8));
    if (!crypto_sha256_stream_update(&ctx, buf, 8))
      goto out;
    uint8_t varbuf = (uint8_t)parsed_out.destination_spk_len;
    if (!crypto_sha256_stream_update(&ctx, &varbuf, 1))
      goto out;
    if (!crypto_sha256_stream_update(&ctx, parsed_out.destination_spk,
                                     (uint32_t)parsed_out.destination_spk_len))
      goto out;
  }
  if (!crypto_sha256_stream_final(&ctx, inner))
    goto out;
  if (!crypto_hash(inner, SHA256_DIGEST_SIZE, stream_session.hash_outputs, SHA256_DIGEST_SIZE,
                   ALG_SHA256))
    goto out;

  ok = true;

out:
  fs_close_global(file);
  return ok;
}

// ---------------------------------------------------------------------------
// Streaming signing: compute tx info for display (parse-on-demand from payload)
// ---------------------------------------------------------------------------
static bool stream_get_tx_info(ew_network_t network, psbt_info_t* info_out) {
  memset(info_out, 0, sizeof(*info_out));

  fs_file_t* file = NULL;
  if (fs_open_global(&file, STREAM_PAYLOAD_PATH, FS_O_RDONLY) != 0)
    return false;

  bool ok = false;

  // Sum input amounts
  uint64_t total_input_sats = 0;
  for (uint32_t i = 0; i < stream_session.num_inputs; i++) {
    raw_tx_input_t inp = {0};
    if (!parse_input_from_flash_f(file, i, &inp))
      goto out;
    if (UINT64_MAX - total_input_sats < inp.amount)
      goto out;
    total_input_sats += inp.amount;
  }

  // Classify outputs and sum output amounts
  uint64_t total_output_sats = 0;
  int external_index = -1;
  int change_index = -1;
  size_t external_count = 0;
  size_t change_count = 0;

  for (uint32_t i = 0; i < stream_session.num_outputs; i++) {
    raw_tx_output_t parsed_out = {0};
    if (!parse_output_from_flash_f(file, i, &parsed_out))
      goto out;
    if (UINT64_MAX - total_output_sats < parsed_out.amount)
      goto out;
    total_output_sats += parsed_out.amount;

    if (parsed_out.has_derivation_path) {
      change_index = (int)i;
      change_count++;
    } else {
      external_index = (int)i;
      external_count++;
    }
  }

  if (external_count > 1 || change_count > 1)
    goto out;
  if (total_input_sats < total_output_sats)
    goto out;
  info_out->fee_amount_sats = total_input_sats - total_output_sats;

  if (external_index >= 0) {
    raw_tx_output_t ext_out = {0};
    if (!parse_output_from_flash_f(file, (uint32_t)external_index, &ext_out))
      goto out;
    ew_error_t err =
      ew_script_to_address(ext_out.destination_spk, ext_out.destination_spk_len, network,
                           info_out->destination_address, DESTINATION_ADDRESS_MAX_LEN);
    if (err != EW_OK)
      goto out;
    info_out->has_destination = true;
    info_out->send_amount_sats = ext_out.amount;
  }

  if (change_index >= 0) {
    raw_tx_output_t chg_out = {0};
    if (!parse_output_from_flash_f(file, (uint32_t)change_index, &chg_out))
      goto out;
    info_out->change_amount_sats = chg_out.amount;
  }

  ok = true;

out:
  fs_close_global(file);
  return ok;
}

// ---------------------------------------------------------------------------
// Streaming signing: validate that every output flagged as change actually
// belongs to our wallet policy (mirrors the check in raw_tx_request_confirmation)
// ---------------------------------------------------------------------------
// Streaming: validate that every input's derivation_path[2] matches the
// expected account index (the current keyset for normal signing, or the
// declared old_account_index for sweeps). Called before user confirmation
// so that a mismatch fails fast — otherwise the user would approve a tx
// that later fails during signature retrieval (stream_precompute_signatures
// / get_tx_signature raises KEYPATH_MISMATCH), which is a poor UX.
static bool stream_validate_input_accounts(uint32_t expected_account_index) {
  fs_file_t* file = NULL;
  if (fs_open_global(&file, STREAM_PAYLOAD_PATH, FS_O_RDONLY) != 0)
    return false;

  bool ok = true;
  for (uint32_t i = 0; i < stream_session.num_inputs; i++) {
    raw_tx_input_t parsed_in = {0};
    if (!parse_input_from_flash_f(file, i, &parsed_in)) {
      ok = false;
      break;
    }
    uint32_t acct = parsed_in.derivation_path[2] & ~0x80000000u;
    if (acct != expected_account_index) {
      LOGE("Strm: in %lu acct %lu != %lu", (unsigned long)i, (unsigned long)acct,
           (unsigned long)expected_account_index);
      ok = false;
      break;
    }
  }

  fs_close_global(file);
  return ok;
}

static bool stream_validate_change_outputs(const wallet_keyset_t* keyset) {
  fs_file_t* file = NULL;
  if (fs_open_global(&file, STREAM_PAYLOAD_PATH, FS_O_RDONLY) != 0)
    return false;

  bool ok = true;
  for (uint32_t i = 0; i < stream_session.num_outputs; i++) {
    raw_tx_output_t parsed_out = {0};
    if (!parse_output_from_flash_f(file, i, &parsed_out)) {
      ok = false;
      break;
    }
    if (!parsed_out.has_derivation_path) {
      if (parsed_out.derivation_path_len != 0) {
        LOGE("Strm: out %lu path mismatch", (unsigned long)i);
        ok = false;
        break;
      }
      continue;
    }
    // The streaming format encodes exactly 5 path elements (BIP84: m/84'/coin'/acct'/change/index).
    // Reject anything else to prevent policy checks on zero-padded or truncated paths.
    if (parsed_out.derivation_path_len != 5) {
      LOGE("Strm: chg %lu pathlen %u", (unsigned long)i, (unsigned)parsed_out.derivation_path_len);
      ok = false;
      break;
    }
    if (!wallet_change_output_belongs_to_policy(
          keyset, parsed_out.derivation_path, parsed_out.derivation_path_len,
          parsed_out.destination_spk, parsed_out.destination_spk_len)) {
      LOGE("Strm: chg %lu policy", (unsigned long)i);
      ok = false;
      break;
    }
  }

  fs_close_global(file);
  return ok;
}

// Streaming counterpart to validate_sweep_tx_shape: sweep is a single-output
// tx whose output is the current keyset's fresh /0/0 receive (see
// validate_sweep_tx_shape for rationale). Input account-consistency is
// handled by stream_validate_input_accounts, called earlier by
// stream_tx_request_confirmation.
static bool stream_validate_sweep_shape(const wallet_keyset_t* keyset) {
  if (stream_session.num_outputs != 1) {
    LOGE("SwSStrm: expected 1 output, got %lu", (unsigned long)stream_session.num_outputs);
    return false;
  }

  uint8_t fresh_spk[sizeof(((raw_tx_output_t*)0)->destination_spk)];
  size_t fresh_spk_len = 0;
  if (!derive_current_fresh_receive_spk(keyset, fresh_spk, sizeof(fresh_spk), &fresh_spk_len)) {
    LOGE("SwSStrm: fresh spk derive");
    return false;
  }

  fs_file_t* file = NULL;
  if (fs_open_global(&file, STREAM_PAYLOAD_PATH, FS_O_RDONLY) != 0)
    return false;

  raw_tx_output_t parsed_out = {0};
  bool parsed = parse_output_from_flash_f(file, 0, &parsed_out);
  fs_close_global(file);
  if (!parsed) {
    return false;
  }

  if (parsed_out.destination_spk_len != fresh_spk_len ||
      memcmp(parsed_out.destination_spk, fresh_spk, fresh_spk_len) != 0) {
    LOGE("SwSStrm: out spk mismatch (not current /0/0)");
    return false;
  }
  return true;
}

// ---------------------------------------------------------------------------
// Streaming signing: request user confirmation
// ---------------------------------------------------------------------------
static fwpb_status stream_tx_request_confirmation(fwpb_wallet_rsp* rsp) {
  wallet_keyset_t keyset = {0};
  if (!wkek_read_and_decrypt(WALLET_KEYSET_PATH, (uint8_t*)&keyset, sizeof(keyset))) {
    LOGE("Keyset load fail");
    return fwpb_status_DESCRIPTOR_NOT_LOADED;
  }

  ew_network_t network =
    (keyset.network == NETWORK_MAINNET) ? EW_NETWORK_MAINNET : EW_NETWORK_TESTNET;

  // Fail fast on account-index mismatch before showing the confirmation UI,
  // so the user is never asked to approve a transaction that cannot be signed.
  // Normal sessions: every input must reference the current keyset's account.
  // Sweep sessions: every input must reference the declared old_account_index.
  const uint32_t expected_input_account = stream_session.sweep.active
                                            ? stream_session.sweep.old_account_index
                                            : (uint32_t)keyset.account_index;
  if (!stream_validate_input_accounts(expected_input_account)) {
    LOGE("Strm: input account validate fail");
    memzero(&keyset, sizeof(keyset));
    return fwpb_status_INVALID_ARGUMENT;
  }

  // Validate change outputs before displaying the transaction to the user.
  // Without this check, a malicious app could label an attacker-controlled output
  // as change, hiding the theft from the on-screen confirmation.
  if (!stream_validate_change_outputs(&keyset)) {
    LOGE("Strm: chg validate fail");
    memzero(&keyset, sizeof(keyset));
    return fwpb_status_ERROR;
  }

  // For sweep sessions, additionally enforce the sweep-specific output
  // invariant: exactly one output, whose scriptPubKey matches the firmware-
  // derived P2WSH at current-keyset m/84'/coin'/current'/0/0. Works for
  // both external destinations and derivation-path destinations — the
  // scriptPubKey match is cryptographically sufficient on its own.
  if (stream_session.sweep.active) {
    if (!stream_validate_sweep_shape(&keyset)) {
      LOGE("Strm: sweep shape fail");
      memzero(&keyset, sizeof(keyset));
      return fwpb_status_INVALID_ARGUMENT;
    }
  }

  memzero(&keyset, sizeof(keyset));

  stream_session_confirmation_data_t confirmation_data = {0};
  if (!stream_get_tx_info(network, &confirmation_data.display_info)) {
    return fwpb_status_ERROR;
  }

  // Compute commitment hash over session fields AND payload hash.
  // This binds to both RAM state (BIP143 hashes, version, lock_time) and flash data
  // (per-input amounts, derivation paths via payload_hash).
  if (!compute_stream_session_commitment_hash(&stream_session, stream_session.payload_hash,
                                              confirmation_data.session_hash)) {
    LOGE("Strm: commit hash fail");
    memzero(&confirmation_data, sizeof(confirmation_data));
    return fwpb_status_ERROR;
  }

  uint8_t response_handle[CONFIRMATION_HANDLE_SIZE], confirmation_handle[CONFIRMATION_HANDLE_SIZE];
  if (confirmation_manager_create(CONFIRMATION_TYPE_SIGN_TRANSACTION, &confirmation_data,
                                  sizeof(stream_session_confirmation_data_t), response_handle,
                                  sizeof(response_handle), confirmation_handle,
                                  sizeof(confirmation_handle)) != CONFIRMATION_RESULT_SUCCESS) {
    memzero(&confirmation_data, sizeof(confirmation_data));
    return fwpb_status_ERROR;
  }

  send_transaction_data_t tx_display = {0};

  // Detect self-send (sweep/consolidation): all outputs belong to the wallet.
  bool is_self_send = !confirmation_data.display_info.has_destination;
  tx_display.flow = is_self_send ? fwpb_money_movement_flow_MONEY_MOVEMENT_FLOW_SELF_SEND
                                 : fwpb_money_movement_flow_MONEY_MOVEMENT_FLOW_SEND;

  // For self-send, display change amount (funds staying in wallet); otherwise send amount.
  uint64_t display_amount = is_self_send ? confirmation_data.display_info.change_amount_sats
                                         : confirmation_data.display_info.send_amount_sats;
  int ret =
    snprintf(tx_display.amount_sats, sizeof(tx_display.amount_sats), "%llu", display_amount);
  if (ret < 0 || ret >= (int)sizeof(tx_display.amount_sats)) {
    confirmation_manager_clear();
    return fwpb_status_ERROR;
  }

  ret = snprintf(tx_display.fee_sats, sizeof(tx_display.fee_sats), "%llu",
                 confirmation_data.display_info.fee_amount_sats);
  if (ret < 0 || ret >= (int)sizeof(tx_display.fee_sats)) {
    confirmation_manager_clear();
    return fwpb_status_ERROR;
  }

  if (!is_self_send) {
    strncpy(tx_display.address, confirmation_data.display_info.destination_address,
            sizeof(tx_display.address) - 1);
    tx_display.address[sizeof(tx_display.address) - 1] = '\0';
  }

  // Populate display preferences stored at stream start time
  tx_display.btc_display_unit = stream_session.btc_display_unit;

  UI_SHOW_EVENT_WITH_DATA(UI_EVENT_START_SEND_TRANSACTION, &tx_display,
                          sizeof(send_transaction_data_t));

  rsp->status = fwpb_status_CONFIRMATION_PENDING;
  memcpy(rsp->response_handle.bytes, response_handle, sizeof(response_handle));
  rsp->response_handle.size = sizeof(response_handle);
  memcpy(rsp->confirmation_handle.bytes, confirmation_handle, sizeof(confirmation_handle));
  rsp->confirmation_handle.size = sizeof(confirmation_handle);
  return fwpb_status_SUCCESS;
}

// ---------------------------------------------------------------------------
// Handler: sign_stream_start (tag 80)
// ---------------------------------------------------------------------------
void key_manager_task_handle_sign_stream_start(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_sign_stream_start_rsp_tag;

  // Unconditionally tear down any prior session
  confirmation_manager_clear();
  signing_session_reset();
  stream_session_reset();
  sap_session_init(&sap_session);

  // Clean up any stale FWUP patch — streaming signing and FWUP share
  // transient flash space and are never concurrent.
  fwup_cleanup_stale_patch();

  const fwpb_sign_stream_start_cmd* req = &cmd->msg.sign_stream_start_cmd;

  if (req->num_inputs == 0 || req->num_outputs == 0) {
    LOGE("SS: zero in/out");
    rsp->msg.sign_stream_start_rsp.rsp_status =
      fwpb_sign_stream_start_rsp_sign_stream_start_rsp_status_ERROR;
    goto out;
  }

  if (req->num_inputs > STREAM_TX_MAX_INPUTS || req->num_outputs > STREAM_TX_MAX_OUTPUTS) {
    LOGE("SS: max in=%lu out=%lu", (unsigned long)req->num_inputs, (unsigned long)req->num_outputs);
    rsp->msg.sign_stream_start_rsp.rsp_status =
      fwpb_sign_stream_start_rsp_sign_stream_start_rsp_status_ERROR;
    goto out;
  }

  if (req->version == 0 || req->version > 2) {
    LOGE("SS: ver %lu", (unsigned long)req->version);
    rsp->msg.sign_stream_start_rsp.rsp_status =
      fwpb_sign_stream_start_rsp_sign_stream_start_rsp_status_ERROR;
    goto out;
  }

  uint32_t expected_size = STREAM_HEADER_SIZE + req->num_inputs * STREAM_INPUT_RECORD_SIZE +
                           req->num_outputs * STREAM_OUTPUT_RECORD_SIZE;
  if (req->payload_size != expected_size) {
    LOGE("SS: sz %lu!=%lu", (unsigned long)req->payload_size, (unsigned long)expected_size);
    rsp->msg.sign_stream_start_rsp.rsp_status =
      fwpb_sign_stream_start_rsp_sign_stream_start_rsp_status_ERROR;
    goto out;
  }

  // Create (or truncate) the flash file for payload storage
  {
    fs_file_t* file = NULL;
    if (fs_open_global(&file, STREAM_PAYLOAD_PATH, FS_O_WRONLY | FS_O_CREAT | FS_O_TRUNC) != 0) {
      LOGE("SS: file create");
      rsp->msg.sign_stream_start_rsp.rsp_status =
        fwpb_sign_stream_start_rsp_sign_stream_start_rsp_status_ERROR;
      goto out;
    }
    fs_close_global(file);
  }

  // Initialize streaming session
  stream_session.state = STREAM_STATE_RECEIVING;
  stream_session.num_inputs = req->num_inputs;
  stream_session.num_outputs = req->num_outputs;
  stream_session.version = req->version;
  stream_session.lock_time = req->lock_time;
  stream_session.expected_payload_size = req->payload_size;
  stream_session.bytes_received = 0;
  stream_session.next_sequence_id = 0;

  // Store display preferences for use at finalize/confirmation time
  stream_session.btc_display_unit = (uint32_t)req->btc_display_unit;

  if (!crypto_sha256_stream_init(&stream_session.commitment_ctx)) {
    LOGE("SS: sha init");
    stream_session_reset();
    rsp->msg.sign_stream_start_rsp.rsp_status =
      fwpb_sign_stream_start_rsp_sign_stream_start_rsp_status_ERROR;
    goto out;
  }

  rsp->msg.sign_stream_start_rsp.rsp_status =
    fwpb_sign_stream_start_rsp_sign_stream_start_rsp_status_SUCCESS;

out:
  proto_send_rsp(cmd, rsp);
}

// ---------------------------------------------------------------------------
// Handler: sign_stream_transfer (tag 81)
// ---------------------------------------------------------------------------
void key_manager_task_handle_sign_stream_transfer(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_sign_stream_transfer_rsp_tag;

  if (stream_session.state != STREAM_STATE_RECEIVING) {
    LOGE("ST: bad state");
    rsp->status = fwpb_status_INVALID_STATE;
    goto out;
  }

  const fwpb_sign_stream_transfer_cmd* req = &cmd->msg.sign_stream_transfer_cmd;

  if (req->sequence_id != stream_session.next_sequence_id) {
    LOGE("ST: seq %lu!=%lu", (unsigned long)req->sequence_id,
         (unsigned long)stream_session.next_sequence_id);
    rsp->status = fwpb_status_INVALID_STATE;
    stream_session_reset();
    goto out;
  }

  uint32_t chunk_size = req->chunk_data.size;
  if (chunk_size == 0) {
    LOGE("ST: empty");
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    stream_session_reset();
    goto out;
  }

  if (stream_session.bytes_received + chunk_size > stream_session.expected_payload_size) {
    LOGE("ST: overflow %lu+%lu>%lu", (unsigned long)stream_session.bytes_received,
         (unsigned long)chunk_size, (unsigned long)stream_session.expected_payload_size);
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    stream_session_reset();
    goto out;
  }

  // Append chunk to flash file
  {
    fs_file_t* file = NULL;
    if (fs_open_global(&file, STREAM_PAYLOAD_PATH, FS_O_WRONLY | FS_O_APPEND) != 0) {
      LOGE("ST: open fail");
      rsp->status = fwpb_status_STORAGE_ERR;
      stream_session_reset();
      goto out;
    }
    int32_t written = fs_file_write(file, req->chunk_data.bytes, chunk_size);
    fs_close_global(file);
    if (written != (int32_t)chunk_size) {
      LOGE("ST: write %ld", (long)written);
      rsp->status = fwpb_status_STORAGE_ERR;
      stream_session_reset();
      goto out;
    }
  }

  // Update commitment hash
  if (!crypto_sha256_stream_update(&stream_session.commitment_ctx, (uint8_t*)req->chunk_data.bytes,
                                   chunk_size)) {
    LOGE("ST: sha upd");
    rsp->status = fwpb_status_ERROR;
    stream_session_reset();
    goto out;
  }

  stream_session.bytes_received += chunk_size;
  stream_session.next_sequence_id++;
  rsp->status = fwpb_status_SUCCESS;

out:
  proto_send_rsp(cmd, rsp);
}

// ---------------------------------------------------------------------------
// Handler: sign_stream_finalize (tag 82)
// ---------------------------------------------------------------------------
void key_manager_task_handle_sign_stream_finalize(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = 0;  // Uses global status + handles, no dedicated rsp tag

  if (stream_session.state != STREAM_STATE_RECEIVING) {
    LOGE("SF: bad state");
    rsp->status = fwpb_status_INVALID_STATE;
    goto out;
  }

  const fwpb_sign_stream_finalize_cmd* req = &cmd->msg.sign_stream_finalize_cmd;

  if (stream_session.bytes_received != stream_session.expected_payload_size) {
    LOGE("SF: sz %lu!=%lu", (unsigned long)stream_session.bytes_received,
         (unsigned long)stream_session.expected_payload_size);
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    stream_session_reset();
    goto out;
  }

  // Finalize commitment SHA256 and compare
  uint8_t computed_hash[SHA256_DIGEST_SIZE];
  if (!crypto_sha256_stream_final(&stream_session.commitment_ctx, computed_hash)) {
    LOGE("SF: sha final");
    rsp->status = fwpb_status_ERROR;
    stream_session_reset();
    goto out;
  }

  if (req->commitment_hash.size != SHA256_DIGEST_SIZE ||
      memcmp(computed_hash, req->commitment_hash.bytes, SHA256_DIGEST_SIZE) != 0) {
    LOGE("SF: commit mismatch");
    rsp->status = fwpb_status_VERIFICATION_FAILED;
    stream_session_reset();
    goto out;
  }

  // Store the verified payload hash for post-approval commitment verification.
  // This hash covers the entire payload (amounts, paths, outputs, etc.).
  memcpy(stream_session.payload_hash, computed_hash, SHA256_DIGEST_SIZE);

  // Compute BIP143 intermediate hashes from the now-complete payload buffer
  if (!stream_compute_bip143_hashes()) {
    LOGE("SF: bip143");
    rsp->status = fwpb_status_ERROR;
    stream_session_reset();
    goto out;
  }

  stream_session.state = STREAM_STATE_FINALIZED;

  // Show confirmation UI and return CONFIRMATION_PENDING
  fwpb_status stream_confirm_status = stream_tx_request_confirmation(rsp);
  if (stream_confirm_status != fwpb_status_SUCCESS) {
    LOGE("SF: conf fail");
    rsp->status = stream_confirm_status;
    stream_session_reset();
    goto out;
  }

out:
  proto_send_rsp(cmd, rsp);
}

// ---------------------------------------------------------------------------
// Handler: get_tx_signature (tag 83)
// ---------------------------------------------------------------------------
void key_manager_task_handle_get_tx_signature(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_get_tx_signature_rsp_tag;
  rsp->status = fwpb_status_ERROR;

  if (stream_session.state != STREAM_STATE_CONFIRMED) {
    LOGE("GS: bad state");
    rsp->status = fwpb_status_INVALID_STATE;
    goto out;
  }

  const fwpb_get_tx_signature_cmd* req = &cmd->msg.get_tx_signature_cmd;
  uint32_t idx = req->input_index;

  if (idx >= stream_session.num_inputs) {
    LOGE("GS: idx %lu>=%lu", (unsigned long)idx, (unsigned long)stream_session.num_inputs);
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }

  // Fast path: read from pre-computed signature cache
  if (stream_session.precompute_done && idx < stream_session.sigs_computed) {
    uint8_t record[SIG_RECORD_SIZE];
    fs_file_t* sigs_file = NULL;
    bool cache_hit = false;
    if (fs_open_global(&sigs_file, STREAM_SIGS_PATH, FS_O_RDONLY) == 0) {
      if (fs_file_seek(sigs_file, (int32_t)(idx * SIG_RECORD_SIZE), FS_SEEK_SET) >= 0 &&
          fs_file_read(sigs_file, record, sizeof(record)) == (int32_t)sizeof(record)) {
        cache_hit = true;
      }
      fs_close_global(sigs_file);
    }
    if (cache_hit) {
      uint8_t sig_len = record[SIG_RECORD_PUBKEY_SIZE];
      if (sig_len == 0 || sig_len > PSBT_SIGNATURE_MAX_LEN) {
        LOGE("GS: cache sig_len %u at %lu", (unsigned int)sig_len, (unsigned long)idx);
      } else {
        memcpy(rsp->msg.get_tx_signature_rsp.pubkey.bytes, record, SIG_RECORD_PUBKEY_SIZE);
        rsp->msg.get_tx_signature_rsp.pubkey.size = SIG_RECORD_PUBKEY_SIZE;
        memcpy(rsp->msg.get_tx_signature_rsp.signature.bytes,
               record + SIG_RECORD_PUBKEY_SIZE + SIG_RECORD_SIGLEN_SIZE, sig_len);
        rsp->msg.get_tx_signature_rsp.signature.size = sig_len;
        rsp->status = fwpb_status_SUCCESS;
        goto out;
      }
    }
    LOGE("GS: cache miss %lu", (unsigned long)idx);
  }

  // Slow path: compute on demand (precompute failed or not done)
  {
    raw_tx_input_t input = {0};
    if (!parse_input_from_flash(idx, &input)) {
      LOGE("GS: parse %lu", (unsigned long)idx);
      goto out;
    }

    wallet_keyset_t keyset = {0};
    if (!wkek_read_and_decrypt(WALLET_KEYSET_PATH, (uint8_t*)&keyset, sizeof(keyset))) {
      LOGE("GS: KS load");
      rsp->status = fwpb_status_STORAGE_ERR;
      goto out;
    }

    if (keyset.version != WALLET_KEYSET_VERSION) {
      LOGE("GS: KS ver %d", keyset.version);
      memzero(&keyset, sizeof(keyset));
      goto out;
    }

    const size_t app_hw_account_depth = 3;
    const size_t server_account_depth = 0;

    // See sign_raw_tx_with_hw_key for rationale.
    const bool is_sweep = stream_session.sweep.active;
    const uint32_t expected_account_index =
      is_sweep ? stream_session.sweep.old_account_index : (uint32_t)keyset.account_index;
    {
      uint32_t input_account = input.derivation_path[2] & ~0x80000000u;
      if (input_account != expected_account_index) {
        LOGE("GS: in %lu acct %lu != %lu", (unsigned long)idx, (unsigned long)input_account,
             (unsigned long)expected_account_index);
        memzero(&keyset, sizeof(keyset));
        goto out;
      }
    }
    const xpub_t* app_xpub_src = is_sweep ? &stream_session.sweep.app_xpub : &keyset.app;
    const xpub_t* hw_xpub_src = is_sweep ? &stream_session.sweep.hw_xpub : &keyset.hw;
    const xpub_t* server_xpub_src = is_sweep ? &stream_session.sweep.server_xpub : &keyset.server;

    uint8_t child_pubkeys[PSBT_P2WSH_MAX_KEYPATHS * PSBT_P2WSH_PUBKEY_LEN];
    const xpub_t* xpubs[PSBT_P2WSH_MAX_KEYPATHS] = {app_xpub_src, hw_xpub_src, server_xpub_src};
    const size_t account_depths[PSBT_P2WSH_MAX_KEYPATHS] = {
      app_hw_account_depth, app_hw_account_depth, server_account_depth};

    uint32_t server_path[PSBT_BIP32_PATH_MAX_LEN];
    size_t server_path_len = input.derivation_path_len;
    if (server_path_len > PSBT_BIP32_PATH_MAX_LEN) {
      LOGE("GS: path %zu", server_path_len);
      memzero(&keyset, sizeof(keyset));
      goto out;
    }
    for (size_t j = 0; j < server_path_len; j++) {
      server_path[j] = input.derivation_path[j] & ~0x80000000u;
    }
    server_path[2] = 0;  // server account is always 0

    for (size_t k = 0; k < PSBT_P2WSH_MAX_KEYPATHS; k++) {
      const uint32_t* path = (k == KEY_INDEX_SERVER) ? server_path : input.derivation_path;
      size_t path_len = (k == KEY_INDEX_SERVER) ? server_path_len : input.derivation_path_len;

      if (!derive_child_pubkey_from_xpub(xpubs[k], path, path_len, account_depths[k],
                                         &child_pubkeys[k * PSBT_P2WSH_PUBKEY_LEN])) {
        LOGE("GS: derive %lu/%zu", (unsigned long)idx, k);
        memzero(&keyset, sizeof(keyset));
        goto out;
      }
    }

    memzero(&keyset, sizeof(keyset));

    psbt_p2wsh_signing_data_t signing_data = {0};
    psbt_error_t err = raw_tx_p2wsh_input_signing_data_precomputed(
      stream_session.hash_prevouts, stream_session.hash_sequence, stream_session.hash_outputs,
      stream_session.lock_time, stream_session.version, &input, child_pubkeys, &signing_data);

    if (err != PSBT_OK) {
      LOGE("GS: sdata %lu:%d", (unsigned long)idx, err);
      goto out;
    }

    key_manager_psbt_input_t km_input = {
      .input_index = idx,
      .signing_data = signing_data,
    };

    key_manager_psbt_signature_t sig = {0};
    size_t sigs_written = 0;
    key_manager_psbt_sign_result_t sign_result =
      key_manager_psbt_sign_p2wsh_inputs(&km_input, 1, &sig, 1, &sigs_written);

    if (sign_result != KEY_MANAGER_PSBT_SIGN_OK || sigs_written != 1) {
      LOGE("GS: sign %lu:%d", (unsigned long)idx, sign_result);
      rsp->status = map_psbt_sign_result(sign_result);
      goto out;
    }

    memcpy(rsp->msg.get_tx_signature_rsp.pubkey.bytes, sig.pubkey, PSBT_P2WSH_PUBKEY_LEN);
    rsp->msg.get_tx_signature_rsp.pubkey.size = PSBT_P2WSH_PUBKEY_LEN;
    memcpy(rsp->msg.get_tx_signature_rsp.signature.bytes, sig.signature, sig.signature_len);
    rsp->msg.get_tx_signature_rsp.signature.size = sig.signature_len;
    rsp->status = fwpb_status_SUCCESS;
  }

out:
  // Clean up session after the last signature is retrieved
  if (rsp->status == fwpb_status_SUCCESS && idx == stream_session.num_inputs - 1) {
    ui_show_confirmation("Success", true);
    stream_session_reset();
  }
  proto_send_rsp(cmd, rsp);
}

// ---------------------------------------------------------------------------
// Handler: get_tx_signatures_batch (tag 84)
// Batched variant — signs up to 10 inputs per NFC round-trip.
// ---------------------------------------------------------------------------

#define BATCH_MAX_SIGNATURES 10

// Sign a single input and populate a fwpb_tx_signature_entry.
// Returns fwpb_status_SUCCESS on success, or an error status.
static fwpb_status sign_single_input(uint32_t idx, const wallet_keyset_t* keyset,
                                     fwpb_tx_signature_entry* out) {
  raw_tx_input_t input = {0};
  if (!parse_input_from_flash(idx, &input)) {
    LOGE("BS: parse %lu", (unsigned long)idx);
    return fwpb_status_ERROR;
  }

  const size_t app_hw_account_depth = 3;
  const size_t server_account_depth = 0;

  // See sign_raw_tx_with_hw_key for rationale.
  const bool is_sweep = stream_session.sweep.active;
  const uint32_t expected_account_index =
    is_sweep ? stream_session.sweep.old_account_index : (uint32_t)keyset->account_index;
  {
    uint32_t input_account = input.derivation_path[2] & ~0x80000000u;
    if (input_account != expected_account_index) {
      LOGE("BS: in %lu acct %lu != %lu", (unsigned long)idx, (unsigned long)input_account,
           (unsigned long)expected_account_index);
      return fwpb_status_INVALID_ARGUMENT;
    }
  }
  const xpub_t* app_xpub_src = is_sweep ? &stream_session.sweep.app_xpub : &keyset->app;
  const xpub_t* hw_xpub_src = is_sweep ? &stream_session.sweep.hw_xpub : &keyset->hw;
  const xpub_t* server_xpub_src = is_sweep ? &stream_session.sweep.server_xpub : &keyset->server;

  uint8_t child_pubkeys[PSBT_P2WSH_MAX_KEYPATHS * PSBT_P2WSH_PUBKEY_LEN];
  const xpub_t* xpubs[PSBT_P2WSH_MAX_KEYPATHS] = {app_xpub_src, hw_xpub_src, server_xpub_src};
  const size_t account_depths[PSBT_P2WSH_MAX_KEYPATHS] = {
    app_hw_account_depth, app_hw_account_depth, server_account_depth};

  uint32_t server_path[PSBT_BIP32_PATH_MAX_LEN];
  size_t server_path_len = input.derivation_path_len;
  if (server_path_len > PSBT_BIP32_PATH_MAX_LEN) {
    return fwpb_status_INVALID_ARGUMENT;
  }
  for (size_t j = 0; j < server_path_len; j++) {
    server_path[j] = input.derivation_path[j] & ~0x80000000u;
  }
  server_path[2] = 0;  // server account is always 0

  for (size_t k = 0; k < PSBT_P2WSH_MAX_KEYPATHS; k++) {
    const uint32_t* path = (k == KEY_INDEX_SERVER) ? server_path : input.derivation_path;
    size_t path_len = (k == KEY_INDEX_SERVER) ? server_path_len : input.derivation_path_len;

    if (!derive_child_pubkey_from_xpub(xpubs[k], path, path_len, account_depths[k],
                                       &child_pubkeys[k * PSBT_P2WSH_PUBKEY_LEN])) {
      LOGE("BS: derive %lu/%zu", (unsigned long)idx, k);
      return fwpb_status_KEY_DERIVATION_FAILED;
    }
  }

  psbt_p2wsh_signing_data_t signing_data = {0};
  psbt_error_t err = raw_tx_p2wsh_input_signing_data_precomputed(
    stream_session.hash_prevouts, stream_session.hash_sequence, stream_session.hash_outputs,
    stream_session.lock_time, stream_session.version, &input, child_pubkeys, &signing_data);

  if (err != PSBT_OK) {
    LOGE("BS: sdata %lu:%d", (unsigned long)idx, err);
    return fwpb_status_ERROR;
  }

  key_manager_psbt_input_t km_input = {
    .input_index = idx,
    .signing_data = signing_data,
  };

  key_manager_psbt_signature_t sig = {0};
  size_t sigs_written = 0;
  key_manager_psbt_sign_result_t sign_result =
    key_manager_psbt_sign_p2wsh_inputs(&km_input, 1, &sig, 1, &sigs_written);

  if (sign_result != KEY_MANAGER_PSBT_SIGN_OK || sigs_written != 1) {
    LOGE("BS: sign %lu:%d", (unsigned long)idx, sign_result);
    return map_psbt_sign_result(sign_result);
  }

  memcpy(out->pubkey.bytes, sig.pubkey, PSBT_P2WSH_PUBKEY_LEN);
  out->pubkey.size = PSBT_P2WSH_PUBKEY_LEN;
  memcpy(out->signature.bytes, sig.signature, sig.signature_len);
  out->signature.size = sig.signature_len;
  return fwpb_status_SUCCESS;
}

void key_manager_task_handle_get_tx_signatures_batch(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();
  rsp->which_msg = fwpb_wallet_rsp_get_tx_signatures_batch_rsp_tag;
  rsp->status = fwpb_status_ERROR;

  if (stream_session.state != STREAM_STATE_CONFIRMED) {
    LOGE("BS: bad state");
    rsp->status = fwpb_status_INVALID_STATE;
    goto out;
  }

  const fwpb_get_tx_signatures_batch_cmd* req = &cmd->msg.get_tx_signatures_batch_cmd;
  uint32_t start = req->start_index;
  uint32_t count = req->count;

  if (count == 0 || count > BATCH_MAX_SIGNATURES) {
    LOGE("BS: cnt %lu", (unsigned long)count);
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }

  if (start >= stream_session.num_inputs) {
    LOGE("BS: idx %lu>=%lu", (unsigned long)start, (unsigned long)stream_session.num_inputs);
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }

  // Clamp count to remaining inputs
  if (start + count > stream_session.num_inputs) {
    count = stream_session.num_inputs - start;
  }

  rsp->msg.get_tx_signatures_batch_rsp.signatures_count = 0;

  // Fast path: read pre-computed signatures from flash cache
  if (stream_session.precompute_done && start + count <= stream_session.sigs_computed) {
    fs_file_t* sigs_file = NULL;
    if (fs_open_global(&sigs_file, STREAM_SIGS_PATH, FS_O_RDONLY) != 0) {
      LOGE("BS: cache open");
      goto fallback;
    }

    // Seek to the start record
    if (fs_file_seek(sigs_file, (int32_t)(start * SIG_RECORD_SIZE), FS_SEEK_SET) < 0) {
      LOGE("BS: cache seek");
      fs_close_global(sigs_file);
      goto fallback;
    }

    for (uint32_t i = 0; i < count; i++) {
      uint8_t record[SIG_RECORD_SIZE];
      int32_t rd = fs_file_read(sigs_file, record, sizeof(record));
      if (rd != (int32_t)sizeof(record)) {
        LOGE("BS: cache rd %lu", (unsigned long)(start + i));
        fs_close_global(sigs_file);
        goto fallback;
      }

      fwpb_tx_signature_entry* out_sig = &rsp->msg.get_tx_signatures_batch_rsp.signatures[i];
      uint8_t sig_len = record[SIG_RECORD_PUBKEY_SIZE];
      if (sig_len == 0 || sig_len > PSBT_SIGNATURE_MAX_LEN) {
        LOGE("BS: sig_len %u at %lu", (unsigned int)sig_len, (unsigned long)(start + i));
        fs_close_global(sigs_file);
        goto fallback;
      }
      memcpy(out_sig->pubkey.bytes, record, SIG_RECORD_PUBKEY_SIZE);
      out_sig->pubkey.size = SIG_RECORD_PUBKEY_SIZE;
      memcpy(out_sig->signature.bytes, record + SIG_RECORD_PUBKEY_SIZE + SIG_RECORD_SIGLEN_SIZE,
             sig_len);
      out_sig->signature.size = sig_len;
      rsp->msg.get_tx_signatures_batch_rsp.signatures_count++;
    }

    fs_close_global(sigs_file);
    rsp->status = fwpb_status_SUCCESS;

    if (start + count >= stream_session.num_inputs) {
      ui_show_confirmation("Success", true);
      stream_session_reset();
    }
    goto out;
  }

fallback:
  // Slow path: compute on demand (precompute failed or incomplete)
  {
    wallet_keyset_t keyset = {0};
    if (!wkek_read_and_decrypt(WALLET_KEYSET_PATH, (uint8_t*)&keyset, sizeof(keyset))) {
      LOGE("BS: KS load");
      rsp->status = fwpb_status_STORAGE_ERR;
      goto out;
    }

    if (keyset.version != WALLET_KEYSET_VERSION) {
      LOGE("BS: KS ver %d", keyset.version);
      memzero(&keyset, sizeof(keyset));
      goto out;
    }

    rsp->msg.get_tx_signatures_batch_rsp.signatures_count = 0;
    for (uint32_t i = 0; i < count; i++) {
      fwpb_tx_signature_entry* out_sig = &rsp->msg.get_tx_signatures_batch_rsp.signatures[i];
      fwpb_status status = sign_single_input(start + i, &keyset, out_sig);
      if (status != fwpb_status_SUCCESS) {
        LOGE("BS: input %lu:%d", (unsigned long)(start + i), status);
        memzero(&keyset, sizeof(keyset));
        rsp->status = status;
        goto out;
      }
      rsp->msg.get_tx_signatures_batch_rsp.signatures_count++;
    }

    memzero(&keyset, sizeof(keyset));
    rsp->status = fwpb_status_SUCCESS;

    if (start + count >= stream_session.num_inputs) {
      ui_show_confirmation("Success", true);
      stream_session_reset();
    }
  }

out:
  proto_send_rsp(cmd, rsp);
}
