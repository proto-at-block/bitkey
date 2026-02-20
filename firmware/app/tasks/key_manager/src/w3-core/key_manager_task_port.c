#include "bip32.h"
#include "ecc.h"
#include "ew.h"
#include "hex.h"
#include "ipc.h"
#include "key_manager_task_impl.h"
#include "log.h"
#include "onboarding.h"
#include "proto_helpers.h"
#include "secure_channel.h"
#include "seed.h"
#include "uc.h"
#include "uc_route.h"
#include "ui_events.h"
#include "ui_messaging.h"
#include "uxc.pb.h"
#include "wallet.h"
#include "wallet.pb.h"
#include "wallet_address.h"
#include "wsm_integrity_key.h"
#include "wstring.h"

#include <string.h>

static void _key_manager_task_handle_uxc_session_response(void* proto, void* UNUSED(context)) {
  ipc_send(key_manager_port, proto, sizeof(proto), IPC_KEY_MANAGER_UXC_SESSION_RESPONSE);
}

void key_manager_task_handle_uxc_session_response(ipc_ref_t* message) {
  fwpb_uxc_msg_device* msg_device = message->object;
  fwpb_secure_channel_establish_rsp* cmd = &msg_device->msg.secure_channel_response;

  if (cmd->protocol_version > SECURE_CHANNEL_PROTOCOL_VERSION) {
    LOGE("Incompatable protocol version: %ld", cmd->protocol_version);
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
    LOGE("UXC secure channel establishment failed: %d", ret);
    uc_free_send_proto(rsp_msg);
    uc_free_recv_proto(message->object);
    return;
  }

  ret = secure_uart_channel_confirm_session(cmd->key_confirmation_tag.bytes);
  uc_free_recv_proto(message->object);
  if (ret != SECURE_CHANNEL_OK) {
    LOGE("UXC secure channel confirmation failed: %d", ret);
    uc_free_send_proto(rsp_msg);
    return;
  }

  rsp->exchange_sig.size = sizeof(rsp->exchange_sig.bytes);
  rsp->key_confirmation_tag.size = sizeof(rsp->key_confirmation_tag.bytes);
  rsp->protocol_version = SECURE_CHANNEL_PROTOCOL_VERSION;
  LOGI("UXC secure channel established, sending confirmation to UXC.");

  (void)uc_send(rsp_msg);
}

// Send ephemeral public key to the UXC on boot to kick off the key establishment process
void key_manager_task_handle_uxc_boot(void) {
  fwpb_uxc_msg_host* msg = uc_alloc_send_proto();
  ASSERT(msg != NULL);
  msg->which_msg = fwpb_uxc_msg_host_secure_channel_establish_tag;
  fwpb_secure_channel_establish_cmd* secure_channel_establish_cmd =
    &msg->msg.secure_channel_establish;

  uint8_t public_key_bytes[SECURE_CHANNEL_PUBKEY_MAX_LEN] = {0};
  uint32_t public_key_len = sizeof(public_key_bytes);
  secure_channel_err_t err = secure_uart_channel_public_key_init(public_key_bytes, &public_key_len);
  if (err != SECURE_CHANNEL_OK) {
    LOGE("Failed to initialize the public key for the uart secure channel: %d", err);
  }
  PROTO_FILL_BYTES(secure_channel_establish_cmd, pk_host, public_key_bytes, public_key_len);
  secure_channel_establish_cmd->protocol_version = SECURE_CHANNEL_PROTOCOL_VERSION;
  (void)uc_send(msg);
}

// BIP32 derivation path constants
#define BIP84_PURPOSE            (84)
#define BIP32_COIN_BTC           (0)
#define BIP32_COIN_TESTNET       (1)
#define BIP32_PATH_DEPTH_ACCOUNT (3)

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
    LOGE("NULL pointer in WSM signature verification");
    return false;
  }

  if (signature_len != ECC_SIG_SIZE) {
    LOGE("Invalid signature length: %zu (expected %d)", signature_len, ECC_SIG_SIZE);
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

void key_manager_task_port_handle_get_address(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_get_address_rsp_tag;
  rsp->status = fwpb_status_ERROR;

  uint32_t address_index = cmd->msg.get_address_cmd.address_index;

  // 1. Load stored keyset
  wallet_keyset_t keyset = {0};
  if (!wkek_read_and_decrypt(WALLET_KEYSET_PATH, (uint8_t*)&keyset, sizeof(keyset))) {
    LOGE("Failed to load keyset - run verify_keys_and_build_descriptor first");
    rsp->status = fwpb_status_STORAGE_ERR;
    goto out;
  }

  if (keyset.version != WALLET_KEYSET_VERSION) {
    LOGE("Unsupported keyset version: %d", keyset.version);
    rsp->status = fwpb_status_ERROR;
    goto out;
  }

  // 2. Derive and generate P2WSH address from keyset
  char address[128] = {0};
  wallet_res_t res = wallet_derive_address(&keyset, address_index, address, sizeof(address));

  if (res != WALLET_RES_OK) {
    LOGE("Address derivation failed at index %lu", (unsigned long)address_index);
    rsp->status = fwpb_status_ERROR;
    goto out;
  }

  // 3. Display address on screen
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

void key_manager_task_port_handle_verify_keys_and_build_descriptor(ipc_ref_t* message) {
  fwpb_wallet_cmd* cmd = proto_get_cmd((uint8_t*)message->object, message->length);
  fwpb_wallet_rsp* rsp = proto_get_rsp();

  rsp->which_msg = fwpb_wallet_rsp_verify_keys_and_build_descriptor_rsp_tag;
  rsp->status = fwpb_status_ERROR;

  fwpb_verify_keys_and_build_descriptor_cmd* vcmd = &cmd->msg.verify_keys_and_build_descriptor_cmd;

  // 1. Validate and extract app spending key components
  if (vcmd->app_spending_key.size != PUBKEY_LENGTH ||
      vcmd->app_spending_key_chaincode.size != CHAINCODE_LENGTH) {
    LOGE("Invalid app key sizes: pubkey=%zu chaincode=%zu", vcmd->app_spending_key.size,
         vcmd->app_spending_key_chaincode.size);
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }
  const uint8_t* app_pubkey = vcmd->app_spending_key.bytes;
  const uint8_t* app_chaincode = vcmd->app_spending_key_chaincode.bytes;

  // Map proto network field to keyset network
  uint8_t app_network = vcmd->network_mainnet ? NETWORK_MAINNET : NETWORK_TESTNET;

  // TODO(W-16055): Support variable account indices via proto field
  // Currently hardcoded to 0 to match App/server behavior
  uint32_t hw_account_index = 0;

  // Validate app spending pubkey
  if (!validate_pubkey(app_pubkey)) {
    LOGE("Invalid app spending public key");
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }

  // 2. Validate and extract server pubkey + chaincode
  if (vcmd->server_spending_key.size != PUBKEY_LENGTH ||
      vcmd->server_spending_key_chaincode.size != CHAINCODE_LENGTH) {
    LOGE("Invalid server key sizes: pubkey=%zu chaincode=%zu", vcmd->server_spending_key.size,
         vcmd->server_spending_key_chaincode.size);
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }
  const uint8_t* server_pubkey = vcmd->server_spending_key.bytes;
  const uint8_t* server_chaincode = vcmd->server_spending_key_chaincode.bytes;

  // Validate server spending pubkey
  if (!validate_pubkey(server_pubkey)) {
    LOGE("Invalid server spending public key");
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }

  // 3. Validate app auth key
  if (vcmd->app_auth_key.size != PUBKEY_LENGTH) {
    LOGE("Invalid app auth key size: %zu", vcmd->app_auth_key.size);
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }

  // Validate app auth pubkey
  if (!validate_pubkey(vcmd->app_auth_key.bytes)) {
    LOGE("Invalid app auth public key");
    rsp->status = fwpb_status_INVALID_ARGUMENT;
    goto out;
  }

  // 4. Derive hardware keys
  extended_key_t hw_auth_key_priv __attribute__((__cleanup__(bip32_zero_key)));
  if (!wallet_get_w1_auth_key(&hw_auth_key_priv)) {
    LOGE("Failed to get HW auth key");
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
    LOGE("Failed to derive HW spending key");
    rsp->status = fwpb_status_FILE_NOT_FOUND;
    goto out;
  }

  extended_key_t hw_auth_key_pub __attribute__((__cleanup__(bip32_zero_key)));
  if (!bip32_priv_to_pub(&hw_auth_key_priv, &hw_auth_key_pub)) {
    LOGE("Failed to derive HW auth public key");
    rsp->status = fwpb_status_ERROR;
    goto out;
  }

  extended_key_t hw_spending_key_pub __attribute__((__cleanup__(bip32_zero_key)));
  if (!bip32_priv_to_pub(&hw_spending_key_priv, &hw_spending_key_pub)) {
    LOGE("Failed to derive HW spending public key");
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
    LOGE("Failed to validate HW public keys");
    rsp->status = fwpb_status_ERROR;
    goto out;
  }

  // 5. Verify WSM signature over all 5 public keys
  // Message format: "WsmIntegrityV1" || "SignPublicKeysV1" || app_auth || hw_auth || app_spending
  // || hw_spending || server_spending
  if (!verify_wsm_signature(vcmd->app_auth_key.bytes, hw_auth_pubkey, app_pubkey,
                            hw_spending_pubkey, server_pubkey, vcmd->wsm_signature.bytes,
                            vcmd->wsm_signature.size)) {
    LOGE("WSM signature verification failed");
    rsp->status = fwpb_status_VERIFICATION_FAILED;
    goto out;
  }

  // 6. Build and store keyset
  wallet_keyset_t keyset = {
    .version = WALLET_KEYSET_VERSION,
    .network = app_network,
  };
  memcpy(keyset.app.pubkey, app_pubkey, PUBKEY_LENGTH);
  memcpy(keyset.app.chaincode, app_chaincode, CHAINCODE_LENGTH);
  memcpy(keyset.hw.pubkey, hw_spending_pubkey, PUBKEY_LENGTH);
  memcpy(keyset.hw.chaincode, hw_spending_key_pub.chaincode, CHAINCODE_LENGTH);
  memcpy(keyset.server.pubkey, server_pubkey, PUBKEY_LENGTH);
  memcpy(keyset.server.chaincode, server_chaincode, CHAINCODE_LENGTH);

  if (!wkek_encrypt_and_store(WALLET_KEYSET_PATH, (const uint8_t*)&keyset, sizeof(keyset))) {
    LOGE("Failed to store keyset");
    rsp->status = fwpb_status_STORAGE_ERR;
    goto out;
  }

  LOGI("Keyset provisioned successfully");
  rsp->status = fwpb_status_SUCCESS;

out:
  memzero(&keyset, sizeof(keyset));
  proto_send_rsp(cmd, rsp);
}

void key_manager_task_register_listeners(void) {
  uc_route_register(fwpb_uxc_msg_device_secure_channel_response_tag,
                    _key_manager_task_handle_uxc_session_response, NULL);
}
