#include "attributes.h"
#include "ecc.h"
#include "key_exchange.h"
#include "log.h"
#include "rtos.h"
#include "secure_channel.h"
#include "secure_channel_common.h"
#include "secure_rng.h"
#include "wstring.h"

secure_channel_ctx_t secure_channel_ctx SHARED_TASK_DATA = {
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

void secure_nfc_channel_init(void) {
  secure_channel_ctx.channel_type = SECURE_NFC_CHANNEL_CORE;
  rtos_mutex_create(&secure_channel_ctx.lock);
}

secure_channel_err_t secure_nfc_channel_establish(
  uint8_t* pk_host, uint32_t pk_host_len, uint8_t* pk_device, uint32_t* pk_device_len,
  uint8_t* exchange_sig, uint32_t exchange_sig_len,
  uint8_t key_confirmation_tag[SECURE_CHANNEL_KEY_CONFIRMATION_TAG_LEN]) {
  // NULL pointer checks for required parameters
  ASSERT(pk_host != NULL);
  ASSERT(pk_device != NULL);
  ASSERT(pk_device_len != NULL);
  ASSERT(exchange_sig != NULL);
  ASSERT(key_confirmation_tag != NULL);
  secure_channel_err_t ret = SECURE_CHANNEL_FAILED_TO_DERIVE_KEY;

  uint8_t our_privkey_buf[EC_PRIVKEY_SIZE_X25519] = {0};
  key_handle_t our_privkey = {
    .alg = ALG_ECC_X25519,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = our_privkey_buf,
    .key.size = sizeof(our_privkey_buf),
    .acl = SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PRIVATE_KEY,
  };
  if (!generate_key(&our_privkey)) {
    ret = SECURE_CHANNEL_FAILED_TO_DERIVE_KEY;
    goto out;
  }

  ASSERT(*pk_device_len >= EC_PUBKEY_SIZE_X25519);
  *pk_device_len = EC_PUBKEY_SIZE_X25519;
  key_handle_t our_pubkey = {
    .alg = ALG_ECC_X25519,
    .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
    .key.bytes = pk_device,
    .key.size = *pk_device_len,
    .acl = SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PUBLIC_KEY,
  };
  if (!export_pubkey(&our_privkey, &our_pubkey)) {
    ret = SECURE_CHANNEL_FAILED_TO_DERIVE_KEY;
    goto out;
  }

  rtos_mutex_lock(&secure_channel_ctx.lock);

  ret = secure_channel_establish_impl(&secure_channel_ctx, pk_host, pk_host_len, &our_privkey,
                                      &our_pubkey, exchange_sig, exchange_sig_len);
  if (ret != SECURE_CHANNEL_OK) {
    goto out;
  }

  ret = secure_channel_compute_confirmation(
    SECURE_NFC_CHANNEL_CORE, &secure_channel_ctx.session_conf_key, key_confirmation_tag);

out:
  rtos_mutex_unlock(&secure_channel_ctx.lock);

  memzero(our_privkey_buf, sizeof(our_privkey_buf));
  return ret;
}

secure_channel_err_t secure_nfc_channel_encrypt(uint8_t* plaintext, uint8_t* ciphertext,
                                                uint32_t len, uint8_t nonce[AES_GCM_IV_LENGTH],
                                                uint8_t mac[AES_GCM_TAG_LENGTH]) {
  return secure_channel_cipher(&secure_channel_ctx, SECURE_CHANNEL_ENCRYPT, plaintext, ciphertext,
                               len, nonce, mac);
}

secure_channel_err_t secure_nfc_channel_decrypt(uint8_t* ciphertext, uint8_t* plaintext,
                                                uint32_t len, uint8_t nonce[AES_GCM_IV_LENGTH],
                                                uint8_t mac[AES_GCM_TAG_LENGTH]) {
  return secure_channel_cipher(&secure_channel_ctx, SECURE_CHANNEL_DECRYPT, ciphertext, plaintext,
                               len, nonce, mac);
}
