#include "key_exchange.h"

#include "aes.h"
#include "attestation.h"
#include "ecc.h"
#include "hkdf.h"
#include "wstring.h"

#include <string.h>

typedef enum {
  KEY_PURPOSE_SEND = 0,
  KEY_PURPOSE_RECV = 1,
  KEY_PURPOSE_CONF = 2,
} key_purpose_t;

// Only x25519 is supported for now, so this value is fixed.
#define DERIVED_SECRET_SIZE (32)

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
#define LABEL_SIZE        (LABEL_PREFIX_SIZE + CRYPTO_SERIAL_SIZE)

// Label for signing the generated public keys.
#define KEY_EXCHANGE_SIGNATURE_LABEL      "KEYEXCHANGE-V1"
#define KEY_EXCHANGE_SIGNATURE_LABEL_SIZE (sizeof(KEY_EXCHANGE_SIGNATURE_LABEL) - 1)

// Prepare a label with the appropriate prefix and unique serial number.
// This serial is the SE serial number, and can be found in the device identity certificate.
//
// Example:
//         Subject: C = US, O = Block Inc, CN = Block Inc EUI:38398FFFFED081B6 S:SE0 ID:MCU
// The serial number is the EUI.
static bool prepare_label(key_purpose_t purpose, bool use_sn, uint8_t* label) {
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

  if (use_sn) {
    return crypto_read_serial(&label[LABEL_PREFIX_SIZE]);
  }
  return true;
}

static bool derive_key_with_purpose(key_purpose_t purpose, bool use_sn, key_handle_t* shared_secret,
                                    uint8_t* key, uint32_t key_size) {
  uint8_t label[LABEL_SIZE] = {0};
  if (!prepare_label(purpose, use_sn, label)) {
    return false;
  }
  key_handle_t key_handle = {
    .alg = ALG_HMAC,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = key,
    .key.size = key_size,
  };
  return crypto_hkdf(shared_secret, ALG_SHA256, NULL, 0, label, sizeof(label), &key_handle);
}

bool crypto_key_exchange(crypto_key_exchange_ctx_t* ctx,
                         crypto_key_exchange_derived_key_material_t* keys) {
  ASSERT(ctx && keys);

  ASSERT(ctx->pk_peer && ctx->pk_peer_len == EC_PUBKEY_SIZE_X25519);
  ASSERT(ctx->pk_us && ctx->pk_us->storage_type == KEY_STORAGE_EXTERNAL_PLAINTEXT &&
         ctx->pk_us->key.size == EC_PUBKEY_SIZE_X25519);
  ASSERT(ctx->sk_us && ctx->sk_us->storage_type == KEY_STORAGE_EXTERNAL_PLAINTEXT &&
         ctx->sk_us->key.size == EC_PRIVKEY_SIZE_X25519);
  ASSERT(ctx->exchange_sig && ctx->exchange_sig_len == ECC_SIG_SIZE);
  ASSERT(keys->send_key && keys->recv_key);

  bool ret = false;

  // Step 1) Derive shared secret

  key_handle_t their_pubkey = {
    .alg = ALG_ECC_X25519,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = ctx->pk_peer,
    .key.size = ctx->pk_peer_len,
    .acl = SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PUBLIC_KEY,
  };

  uint8_t intermediate_curve_point_buf[DERIVED_SECRET_SIZE] = {0};
  key_handle_t shared_secret_handle = {
    .alg = ALG_HMAC,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = intermediate_curve_point_buf,
    .key.size = sizeof(intermediate_curve_point_buf),
  };
  ret = crypto_ecc_compute_shared_secret(ctx->sk_us, &their_pubkey, &shared_secret_handle);
  if (!ret) {
    goto out;
  }

  // Step 2.1) HKDF the shared secret to derive the send key
  ret = derive_key_with_purpose(KEY_PURPOSE_SEND, ctx->use_sn, &shared_secret_handle,
                                keys->send_key, AES_256_LENGTH_BYTES);
  if (!ret) {
    goto out;
  }

  // Step 2.2) HKDF the shared secret to derive the recv key
  ret = derive_key_with_purpose(KEY_PURPOSE_RECV, ctx->use_sn, &shared_secret_handle,
                                keys->recv_key, AES_256_LENGTH_BYTES);
  if (!ret) {
    goto out;
  }

  // Step 2.3) HKDF the shared secret to derive the conf key
  ret = derive_key_with_purpose(KEY_PURPOSE_CONF, ctx->use_sn, &shared_secret_handle,
                                keys->conf_key, AES_256_LENGTH_BYTES);
  if (!ret) {
    goto out;
  }

  // Step 3) Sign with the unique device identity the following:
  //  A context label
  //  Our public key
  //  Peer's public key

  uint8_t signing_input[KEY_EXCHANGE_SIGNATURE_LABEL_SIZE + EC_PUBKEY_SIZE_X25519 * 2] = {0};
  memcpy(signing_input, KEY_EXCHANGE_SIGNATURE_LABEL, KEY_EXCHANGE_SIGNATURE_LABEL_SIZE);
  memcpy(&signing_input[KEY_EXCHANGE_SIGNATURE_LABEL_SIZE], ctx->pk_us->key.bytes,
         EC_PUBKEY_SIZE_X25519);
  memcpy(&signing_input[KEY_EXCHANGE_SIGNATURE_LABEL_SIZE + EC_PUBKEY_SIZE_X25519], ctx->pk_peer,
         EC_PUBKEY_SIZE_X25519);

  ret = crypto_sign_with_device_identity(signing_input, sizeof(signing_input), ctx->exchange_sig,
                                         ctx->exchange_sig_len);
  if (!ret) {
    goto out;
  }

  ret = true;

out:
  if (!ret) {
    memzero(intermediate_curve_point_buf, sizeof(intermediate_curve_point_buf));
    memzero(keys->recv_key, AES_256_LENGTH_BYTES);
    memzero(keys->send_key, AES_256_LENGTH_BYTES);
  }
  return ret;
}
