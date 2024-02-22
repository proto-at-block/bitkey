#include "key_exchange.h"

#include "ecc.h"
#include "hkdf.h"
#include "secure_engine.h"
#include "secutils.h"
#include "sl_se_manager_types.h"

#include <string.h>

typedef enum {
  KEY_PURPOSE_SEND = 0,
  KEY_PURPOSE_RECV = 1,
  KEY_PURPOSE_CONF = 2,
} key_purpose_t;

// Only x25519 is supported for now, so this value is fixed.
#define X25519_PUBKEY_SIZE  (32)
#define DERIVED_SECRET_SIZE (32)

#define X25519_KEYPAIR_SIZE (64)

// For sending (encrypting) messages from the bitkey to the host.
#define KEY_EXCHANGE_DERIVATION_SENDING_LABEL "BK2HOST"

// For receiving (decrypting) messages from the host to the bitkey.
#define KEY_EXCHANGE_DERIVATION_RECEIVING_LABEL "HOST2BK"

// For key confirmation.
#define KEY_EXCHANGE_DERIVATION_CONFIRMATION_LABEL "CONFIRM"

_Static_assert(sizeof(KEY_EXCHANGE_DERIVATION_SENDING_LABEL) ==
                 sizeof(KEY_EXCHANGE_DERIVATION_RECEIVING_LABEL),
               "Sending and receiving labels must be the same size");
_Static_assert(sizeof(KEY_EXCHANGE_DERIVATION_SENDING_LABEL) ==
                 sizeof(KEY_EXCHANGE_DERIVATION_CONFIRMATION_LABEL),
               "Sending and confirmation labels must be the same size");

#define LABEL_PREFIX_SIZE (sizeof(KEY_EXCHANGE_DERIVATION_SENDING_LABEL) - 1)
#define LABEL_SIZE        (LABEL_PREFIX_SIZE + SE_ACTUAL_SERIAL_SIZE)

// Label for signing the generated public keys.
#define KEY_EXCHANGE_SIGNATURE_LABEL      "KEYEXCHANGE-V1"
#define KEY_EXCHANGE_SIGNATURE_LABEL_SIZE (sizeof(KEY_EXCHANGE_SIGNATURE_LABEL) - 1)

// Message for key confirmation.
#define KEY_CONFIRMATION_MESSAGE     "KEYCONFIRM-V1"
#define KEY_CONFIRMATION_MESSAGE_LEN (sizeof(KEY_CONFIRMATION_MESSAGE) - 1)

// Prepare a label with the appropriate prefix and unique serial number.
// This serial is the SE serial number, and can be found in the device identity certificate.
//
// Example:
//         Subject: C = US, O = Block Inc, CN = Block Inc EUI:38398FFFFED081B6 S:SE0 ID:MCU
// The serial number is the EUI.
static bool prepare_label(key_purpose_t purpose, uint8_t label[LABEL_SIZE]) {
  switch (purpose) {
    case KEY_PURPOSE_SEND:
      memcpy(label, KEY_EXCHANGE_DERIVATION_SENDING_LABEL, LABEL_PREFIX_SIZE);
      break;
    case KEY_PURPOSE_RECV:
      memcpy(label, KEY_EXCHANGE_DERIVATION_RECEIVING_LABEL, LABEL_PREFIX_SIZE);
      break;
    case KEY_PURPOSE_CONF:
      memcpy(label, KEY_EXCHANGE_DERIVATION_CONFIRMATION_LABEL, LABEL_PREFIX_SIZE);
      break;
    default:
      return false;
  }

  uint8_t zero_padded_serial[SE_SERIAL_SIZE] = {0};
  if (se_read_serial(zero_padded_serial) != SL_STATUS_OK) {
    return false;
  }

  memcpy(&label[LABEL_PREFIX_SIZE], &zero_padded_serial[SE_ACTUAL_SERIAL_START],
         SE_ACTUAL_SERIAL_SIZE);

  return true;
}

bool crypto_key_exchange(crypto_key_exchange_ctx_t* ctx,
                         crypto_key_exchange_derived_key_material_t* keys) {
  ASSERT(ctx && keys);

  ASSERT(ctx->pk_peer && ctx->pk_peer_len == X25519_PUBKEY_SIZE);
  ASSERT(ctx->pk_us && ctx->pk_us_len == X25519_PUBKEY_SIZE);
  ASSERT(ctx->exchange_sig && ctx->exchange_sig_len == ECC_SIG_SIZE);
  ASSERT(ctx->key_confirmation_tag && ctx->key_confirmation_tag_len == KEY_CONFIRMATION_TAG_LEN);
  ASSERT(keys->send_key && keys->recv_key);

  bool ret = false;

  sl_se_command_context_t cmd_ctx = {0};
  sl_status_t status = sl_se_init_command_context(&cmd_ctx);
  if (status != SL_STATUS_OK) {
    goto out;
  }

  uint8_t our_keypair_buf[X25519_KEYPAIR_SIZE] = {0};
  sl_se_key_descriptor_t our_ephemeral_keypair = {
    .type = SL_SE_KEY_TYPE_ECC_X25519,
    .flags = SL_SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PRIVATE_KEY |
             SL_SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PUBLIC_KEY,
    .storage.method = SL_SE_KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .storage.location.buffer.pointer = our_keypair_buf,
    .storage.location.buffer.size = sizeof(our_keypair_buf),
  };

  sl_se_key_descriptor_t our_ephemeral_pubkey = {
    .type = SL_SE_KEY_TYPE_ECC_X25519,
    .flags = SL_SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PUBLIC_KEY,
    .storage.method = SL_SE_KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .storage.location.buffer.pointer = ctx->pk_us,
    .storage.location.buffer.size = ctx->pk_us_len,
  };

  sl_se_key_descriptor_t their_pub_key = {
    .type = SL_SE_KEY_TYPE_ECC_X25519,
    .flags = SL_SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PUBLIC_KEY,
    .storage.method = SL_SE_KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .storage.location.buffer.pointer = ctx->pk_peer,
    .storage.location.buffer.size = ctx->pk_peer_len,
  };

  uint8_t intermediate_curve_point_buf[DERIVED_SECRET_SIZE] = {0};
  sl_se_key_descriptor_t intermediate_curve_point = {
    .type = SL_SE_KEY_TYPE_SYMMETRIC,
    .size = DERIVED_SECRET_SIZE,
    .storage.method = SL_SE_KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .storage.location.buffer.pointer = intermediate_curve_point_buf,
    .storage.location.buffer.size = sizeof(intermediate_curve_point_buf),
  };

  sl_se_key_descriptor_t send_key = {
    .type = SL_SE_KEY_TYPE_SYMMETRIC,
    .size = AES_256_LENGTH_BYTES,
    .storage.method = SL_SE_KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .storage.location.buffer.pointer = keys->send_key,
    .storage.location.buffer.size = AES_256_LENGTH_BYTES,
  };

  sl_se_key_descriptor_t recv_key = {
    .type = SL_SE_KEY_TYPE_SYMMETRIC,
    .size = AES_256_LENGTH_BYTES,
    .storage.method = SL_SE_KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .storage.location.buffer.pointer = keys->recv_key,
    .storage.location.buffer.size = AES_256_LENGTH_BYTES,
  };

  sl_se_key_descriptor_t conf_key = {
    .type = SL_SE_KEY_TYPE_SYMMETRIC,
    .size = AES_256_LENGTH_BYTES,
    .storage.method = SL_SE_KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .storage.location.buffer.pointer = keys->conf_key,
    .storage.location.buffer.size = AES_256_LENGTH_BYTES,
  };

  // Step 1) Generate our ephermeral keypair

  status = sl_se_generate_key(&cmd_ctx, &our_ephemeral_keypair);
  if (status != SL_STATUS_OK) {
    goto out;
  }

  // Step 2) Derive shared secret

  status = sl_se_init_command_context(&cmd_ctx);
  if (status != SL_STATUS_OK) {
    goto out;
  }

  status = sl_se_ecdh_compute_shared_secret(&cmd_ctx, &our_ephemeral_keypair, &their_pub_key,
                                            &intermediate_curve_point);
  if (status != SL_STATUS_OK) {
    goto out;
  }

  // Step 2.1) HKDF the shared secret to derive the send key
  status = sl_se_init_command_context(&cmd_ctx);
  if (status != SL_STATUS_OK) {
    goto out;
  }

  uint8_t label[LABEL_SIZE] = {0};
  if (!prepare_label(KEY_PURPOSE_SEND, label)) {
    goto out;
  }

  status = sl_se_derive_key_hkdf(&cmd_ctx, &intermediate_curve_point, SL_SE_HASH_SHA256, NULL, 0,
                                 (const unsigned char*)label, sizeof(label), &send_key);
  if (status != SL_STATUS_OK) {
    goto out;
  }

  // Step 2.2) HKDF the shared secret to derive the recv key
  status = sl_se_init_command_context(&cmd_ctx);
  if (status != SL_STATUS_OK) {
    goto out;
  }

  if (!prepare_label(KEY_PURPOSE_RECV, label)) {
    goto out;
  }

  status = sl_se_derive_key_hkdf(&cmd_ctx, &intermediate_curve_point, SL_SE_HASH_SHA256, NULL, 0,
                                 (const unsigned char*)label, sizeof(label), &recv_key);
  if (status != SL_STATUS_OK) {
    goto out;
  }

  // Step 2.3) HKDF the shared secret to derive the conf key
  status = sl_se_init_command_context(&cmd_ctx);
  if (status != SL_STATUS_OK) {
    goto out;
  }

  if (!prepare_label(KEY_PURPOSE_CONF, label)) {
    goto out;
  }

  status = sl_se_derive_key_hkdf(&cmd_ctx, &intermediate_curve_point, SL_SE_HASH_SHA256, NULL, 0,
                                 (const unsigned char*)label, sizeof(label), &conf_key);
  if (status != SL_STATUS_OK) {
    goto out;
  }

  // Step 3) Export our public key

  status = sl_se_init_command_context(&cmd_ctx);
  if (status != SL_STATUS_OK) {
    goto out;
  }

  status = sl_se_export_public_key(&cmd_ctx, &our_ephemeral_keypair, &our_ephemeral_pubkey);
  if (status != SL_STATUS_OK) {
    goto out;
  }

  // Step 4) Sign with the unique device identity the following:
  //  A context label
  //  Our public key
  //  Peer's public key

  uint8_t signing_input[KEY_EXCHANGE_SIGNATURE_LABEL_SIZE + X25519_PUBKEY_SIZE * 2] = {0};
  memcpy(signing_input, KEY_EXCHANGE_SIGNATURE_LABEL, KEY_EXCHANGE_SIGNATURE_LABEL_SIZE);
  memcpy(&signing_input[KEY_EXCHANGE_SIGNATURE_LABEL_SIZE], ctx->pk_us, X25519_PUBKEY_SIZE);
  memcpy(&signing_input[KEY_EXCHANGE_SIGNATURE_LABEL_SIZE + X25519_PUBKEY_SIZE], ctx->pk_peer,
         X25519_PUBKEY_SIZE);

  status = sl_se_init_command_context(&cmd_ctx);
  if (status != SL_STATUS_OK) {
    goto out;
  }

  status = se_sign_with_device_identity_key(signing_input, sizeof(signing_input), ctx->exchange_sig,
                                            ctx->exchange_sig_len);
  if (status != SL_STATUS_OK) {
    goto out;
  }

  // Step 5) Provide the key confirmation tag
  status = sl_se_init_command_context(&cmd_ctx);
  if (status != SL_STATUS_OK) {
    goto out;
  }

  uint8_t hmac_result[SHA256_DIGEST_SIZE] = {0};
  status =
    se_hmac(&cmd_ctx, &conf_key, SL_SE_HASH_SHA256, (const unsigned char*)KEY_CONFIRMATION_MESSAGE,
            KEY_CONFIRMATION_MESSAGE_LEN, hmac_result, sizeof(hmac_result));
  if (status != SL_STATUS_OK) {
    goto out;
  }

  if (ctx->key_confirmation_tag_len > sizeof(hmac_result)) {
    goto out;
  }
  memcpy(ctx->key_confirmation_tag, hmac_result, ctx->key_confirmation_tag_len);

  ret = true;

out:
  if (!ret) {
    memzero(our_keypair_buf, sizeof(our_keypair_buf));
    memzero(intermediate_curve_point_buf, sizeof(intermediate_curve_point_buf));
    memzero(keys->recv_key, AES_256_LENGTH_BYTES);
    memzero(keys->send_key, AES_256_LENGTH_BYTES);
  }
  return ret;
}
