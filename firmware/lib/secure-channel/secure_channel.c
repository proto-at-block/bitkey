#include "secure_channel.h"

#include "attributes.h"
#include "key_exchange.h"
#include "log.h"
#include "rtos.h"
#include "secure_rng.h"
#include "secutils.h"

static struct {
  uint8_t send_key_buf[AES_256_LENGTH_BYTES];
  uint8_t recv_key_buf[AES_256_LENGTH_BYTES];
  uint8_t conf_key_buf[AES_256_LENGTH_BYTES];
  key_handle_t session_send_key;
  key_handle_t session_recv_key;
  key_handle_t session_conf_key;
  bool established;
  rtos_mutex_t lock;
} secure_channel_ctx SHARED_TASK_DATA = {
  .send_key_buf = {0},
  .recv_key_buf = {0},
  .conf_key_buf = {0},
  .session_send_key =
    {
      .alg = ALG_AES_256,
      .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
      .key.bytes = secure_channel_ctx.send_key_buf,
      .key.size = sizeof(secure_channel_ctx.send_key_buf),
    },
  .session_recv_key =
    {
      .alg = ALG_AES_256,
      .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
      .key.bytes = secure_channel_ctx.recv_key_buf,
      .key.size = sizeof(secure_channel_ctx.recv_key_buf),
    },
  .session_conf_key =
    {
      .alg = ALG_HMAC,
      .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
      .key.bytes = secure_channel_ctx.conf_key_buf,
      .key.size = sizeof(secure_channel_ctx.conf_key_buf),
    },
  .established = false,
};

void secure_channel_init(void) {
  rtos_mutex_create(&secure_channel_ctx.lock);
}

static secure_channel_err_t secure_channel_cipher(bool encrypt, uint8_t* data_in, uint8_t* data_out,
                                                  uint32_t data_len,
                                                  uint8_t nonce[AES_GCM_IV_LENGTH],
                                                  uint8_t mac[AES_GCM_TAG_LENGTH]) {
  ASSERT(data_in && data_out && nonce && mac);

  rtos_mutex_lock(&secure_channel_ctx.lock);

  secure_channel_err_t result = SECURE_CHANNEL_CIPHER_FAILED;

  if (!secure_channel_ctx.established) {
    LOGE("secure channel not established");
    result = SECURE_CHANNEL_NO_KEY;
    goto out;
  }

  bool ok = false;
  if (encrypt) {
    if (!crypto_random(nonce, AES_GCM_IV_LENGTH)) {
      goto out;
    }
    ok = aes_gcm_encrypt(data_in, data_out, data_len, nonce, mac, NULL, 0,
                         &secure_channel_ctx.session_send_key);
  } else {
    ok = aes_gcm_decrypt(data_in, data_out, data_len, nonce, mac, NULL, 0,
                         &secure_channel_ctx.session_recv_key);
  }

  result = ok ? SECURE_CHANNEL_OK : SECURE_CHANNEL_CIPHER_FAILED;

out:
  rtos_mutex_unlock(&secure_channel_ctx.lock);
  return result;
}

secure_channel_err_t secure_channel_establish(
  uint8_t* pk_host, uint32_t pk_host_len, uint8_t* pk_device, uint32_t* pk_device_len,
  uint8_t* exchange_sig, uint32_t exchange_sig_len,
  uint8_t key_confirmation_tag[SECURE_CHANNEL_KEY_CONFIRMATION_TAG_LEN]) {
  ASSERT(pk_host && pk_device && pk_device_len && exchange_sig && key_confirmation_tag);

  // Always establish new keys, even if we already have one. The other party may have lost theirs.
  memzero(secure_channel_ctx.send_key_buf, sizeof(secure_channel_ctx.send_key_buf));
  memzero(secure_channel_ctx.recv_key_buf, sizeof(secure_channel_ctx.recv_key_buf));
  secure_channel_ctx.established = false;

  if (*pk_device_len > SECURE_CHANNEL_PUBKEY_MAX_LEN) {
    LOGE("pubkey may be at most %d bytes", SECURE_CHANNEL_PUBKEY_MAX_LEN);
    return SECURE_CHANNEL_FAILED_TO_DERIVE_KEY;
  }

  // The API for secure-channel allows for up to 64 byte pubkeys. But, for now, we only
  // support X25519. So, we set the pubkey size here.
  *pk_device_len = CRYPTO_KEY_EXCHANGE_PUBKEY_SIZE;

  crypto_key_exchange_ctx_t key_exchange_ctx = {
    .pk_peer = pk_host,
    .pk_peer_len = pk_host_len,
    .pk_us = pk_device,
    .pk_us_len = *pk_device_len,
    .exchange_sig = exchange_sig,
    .exchange_sig_len = exchange_sig_len,
    .key_confirmation_tag = key_confirmation_tag,
    .key_confirmation_tag_len = SECURE_CHANNEL_KEY_CONFIRMATION_TAG_LEN,
  };

  crypto_key_exchange_derived_key_material_t key_material = {
    .send_key = secure_channel_ctx.send_key_buf,
    .recv_key = secure_channel_ctx.recv_key_buf,
    .conf_key = secure_channel_ctx.conf_key_buf,
  };

  if (!crypto_key_exchange(&key_exchange_ctx, &key_material)) {
    LOGE("crypto_key_exchange failed");
    return SECURE_CHANNEL_FAILED_TO_DERIVE_KEY;
  }

  secure_channel_ctx.established = true;

  return SECURE_CHANNEL_OK;
}

secure_channel_err_t secure_channel_encrypt(uint8_t* plaintext, uint8_t* ciphertext, uint32_t len,
                                            uint8_t nonce[AES_GCM_IV_LENGTH],
                                            uint8_t mac[AES_GCM_TAG_LENGTH]) {
  return secure_channel_cipher(true, plaintext, ciphertext, len, nonce, mac);
}

secure_channel_err_t secure_channel_decrypt(uint8_t* ciphertext, uint8_t* plaintext, uint32_t len,
                                            uint8_t nonce[AES_GCM_IV_LENGTH],
                                            uint8_t mac[AES_GCM_TAG_LENGTH]) {
  return secure_channel_cipher(false, ciphertext, plaintext, len, nonce, mac);
}
