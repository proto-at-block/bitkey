#include "ecc.h"
#include "secure_channel.h"
#include "secure_channel_common.h"
#include "sysevent.h"
#include "wstring.h"

#include <string.h>

static secure_channel_ctx_t secure_channel_ctx SHARED_TASK_DATA = {
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
  .channel_type = SECURE_CHANNEL_NONE,
};

static struct {
  uint8_t privkey_buf[EC_PRIVKEY_SIZE_X25519];
  key_handle_t privkey;
  uint8_t pubkey_buf[EC_PUBKEY_SIZE_X25519];
  key_handle_t pubkey;
  uint32_t recv_sequence_number;
  uint32_t send_sequence_number;
  bool have_keys;
  bool confirmed;
} uxc_channel_local_state SHARED_TASK_DATA = {
  .privkey_buf = {0},
  .privkey =
    {
      .alg = ALG_ECC_X25519,
      .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
      .key.bytes = uxc_channel_local_state.privkey_buf,
      .key.size = sizeof(uxc_channel_local_state.privkey_buf),
      .acl = SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PRIVATE_KEY,
    },
  .pubkey_buf = {0},
  .pubkey =
    {
      .alg = ALG_ECC_X25519,
      .storage_type = KEY_STORAGE_EXTERNAL_PLAINTEXT,
      .key.bytes = uxc_channel_local_state.pubkey_buf,
      .key.size = sizeof(uxc_channel_local_state.pubkey_buf),
      .acl = SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PUBLIC_KEY,
    },
  .recv_sequence_number = 0,
  .send_sequence_number = 0,
  .have_keys = false,
  .confirmed = false,
};

void secure_uart_channel_init(secure_channel_type_t channel_type) {
  ASSERT(channel_type == SECURE_UART_CHANNEL_CORE || channel_type == SECURE_UART_CHANNEL_UXC);
  secure_channel_ctx.channel_type = channel_type;
  rtos_mutex_create(&secure_channel_ctx.lock);
}

bool secure_uart_channel_confirmed(void) {
  rtos_mutex_lock(&secure_channel_ctx.lock);
  bool confirmed = uxc_channel_local_state.confirmed;
  rtos_mutex_unlock(&secure_channel_ctx.lock);
  return confirmed;
}

secure_channel_err_t secure_uart_channel_confirm_session(uint8_t* received_tag) {
  rtos_mutex_lock(&secure_channel_ctx.lock);
  ASSERT(secure_channel_ctx.channel_type != SECURE_CHANNEL_NONE);
  uint8_t expected_tag[SECURE_CHANNEL_KEY_CONFIRMATION_TAG_LEN] = {0};

  // The received confirmation tag was generated with the peer's channel type.
  secure_channel_type_t channel_type = SECURE_CHANNEL_NONE;
  if (secure_channel_ctx.channel_type == SECURE_UART_CHANNEL_UXC) {
    channel_type = SECURE_UART_CHANNEL_CORE;
  } else {
    channel_type = SECURE_UART_CHANNEL_UXC;
  }

  secure_channel_err_t ret = secure_channel_compute_confirmation(
    channel_type, &secure_channel_ctx.session_conf_key, expected_tag);
  if (ret != SECURE_CHANNEL_OK) {
    goto out;
  }

  if (memcmp_s(expected_tag, received_tag, SECURE_CHANNEL_KEY_CONFIRMATION_TAG_LEN) != 0) {
    ret = SECURE_CHANNEL_CONFIRMATION_FAILED;
    goto out;
  }
  uxc_channel_local_state.confirmed = true;
  uxc_channel_local_state.recv_sequence_number = 0;
  uxc_channel_local_state.send_sequence_number = 0;
  sysevent_set(SYSEVENT_UXC_SECURE_COMMS_ESTABLISHED);
out:
  rtos_mutex_unlock(&secure_channel_ctx.lock);
  return ret;
}

static secure_channel_err_t init_keys(void) {
  if (!uxc_channel_local_state.have_keys) {
    if (!generate_key(&uxc_channel_local_state.privkey)) {
      return SECURE_CHANNEL_FAILED_TO_DERIVE_KEY;
    }

    if (!export_pubkey(&uxc_channel_local_state.privkey, &uxc_channel_local_state.pubkey)) {
      memzero(uxc_channel_local_state.privkey_buf, sizeof(uxc_channel_local_state.privkey_buf));
      return SECURE_CHANNEL_FAILED_TO_DERIVE_KEY;
    }
    uxc_channel_local_state.have_keys = true;
  }
  return SECURE_CHANNEL_OK;
}

secure_channel_err_t secure_uart_channel_public_key_init(uint8_t* public_key,
                                                         uint32_t* pubkey_len) {
  ASSERT(secure_channel_ctx.channel_type != SECURE_CHANNEL_NONE);
  ASSERT(*pubkey_len >= EC_PUBKEY_SIZE_X25519);
  rtos_mutex_lock(&secure_channel_ctx.lock);
  secure_channel_err_t err = init_keys();
  if (err != SECURE_CHANNEL_OK) {
    goto out;
  }
  memcpy(public_key, uxc_channel_local_state.pubkey_buf, EC_PUBKEY_SIZE_X25519);
  *pubkey_len = EC_PUBKEY_SIZE_X25519;

out:
  rtos_mutex_unlock(&secure_channel_ctx.lock);
  return err;
}

secure_channel_err_t secure_uart_channel_establish(uint8_t* pk_peer, uint32_t pk_peer_len,
                                                   uint8_t* pk_device, uint32_t* pk_device_len,
                                                   uint8_t* exchange_sig, uint32_t exchange_sig_len,
                                                   uint8_t* key_confirmation_tag) {
  // NULL pointer checks for required parameters
  ASSERT(secure_channel_ctx.channel_type != SECURE_CHANNEL_NONE);
  ASSERT(pk_peer != NULL);
  ASSERT(exchange_sig != NULL);
  ASSERT(key_confirmation_tag != NULL);
  secure_channel_err_t ret = SECURE_CHANNEL_FAILED_TO_DERIVE_KEY;

  rtos_mutex_lock(&secure_channel_ctx.lock);

  uxc_channel_local_state.confirmed = false;

  ret = init_keys();
  if (ret != SECURE_CHANNEL_OK) {
    goto out;
  }

  if (pk_device != NULL) {
    ASSERT(pk_device_len != NULL);
    ASSERT(*pk_device_len >= EC_PUBKEY_SIZE_X25519);
    memcpy(pk_device, uxc_channel_local_state.pubkey_buf, EC_PUBKEY_SIZE_X25519);
    *pk_device_len = EC_PUBKEY_SIZE_X25519;
  }

  ret = secure_channel_establish_impl(
    &secure_channel_ctx, pk_peer, pk_peer_len, &uxc_channel_local_state.privkey,
    &uxc_channel_local_state.pubkey, exchange_sig, exchange_sig_len);
  if (ret != SECURE_CHANNEL_OK) {
    goto out;
  }

  ret = secure_channel_compute_confirmation(
    secure_channel_ctx.channel_type, &secure_channel_ctx.session_conf_key, key_confirmation_tag);
out:
  rtos_mutex_unlock(&secure_channel_ctx.lock);
  return ret;
}

secure_bool_t secure_uart_channel_encrypt(uint8_t const* plaintext, uint8_t* ciphertext,
                                          uint32_t len, uint8_t const* aad, uint32_t aad_len,
                                          uint8_t nonce[AES_GCM_IV_LENGTH],
                                          uint8_t mac[AES_GCM_TAG_LENGTH]) {
  ASSERT(secure_channel_ctx.channel_type != SECURE_CHANNEL_NONE);
  if (!uxc_channel_local_state.confirmed) {
    return SECURE_FALSE;
  }
  if (secure_channel_cipher(&secure_channel_ctx, SECURE_CHANNEL_ENCRYPT, plaintext, ciphertext, len,
                            aad, aad_len, nonce, mac) != SECURE_CHANNEL_OK) {
    return SECURE_FALSE;
  }
  return SECURE_TRUE;
}

secure_bool_t secure_uart_channel_decrypt(uint8_t const* ciphertext, uint8_t* plaintext,
                                          uint32_t len, uint8_t const* aad, uint32_t aad_len,
                                          uint8_t nonce[AES_GCM_IV_LENGTH],
                                          uint8_t mac[AES_GCM_TAG_LENGTH]) {
  ASSERT(secure_channel_ctx.channel_type != SECURE_CHANNEL_NONE);
  if (!uxc_channel_local_state.confirmed) {
    return SECURE_FALSE;
  }
  if (secure_channel_cipher(&secure_channel_ctx, SECURE_CHANNEL_DECRYPT, ciphertext, plaintext, len,
                            aad, aad_len, nonce, mac) != SECURE_CHANNEL_OK) {
    return SECURE_FALSE;
  }
  return SECURE_TRUE;
}

uint32_t secure_uart_channel_get_send_seq_number(void) {
  rtos_mutex_lock(&secure_channel_ctx.lock);
  uint32_t const prev_seq = uxc_channel_local_state.send_sequence_number;
  if (prev_seq == UINT32_MAX) {
    ASSERT(false);
  }
  uint32_t const new_seq = ++uxc_channel_local_state.send_sequence_number;
  rtos_mutex_unlock(&secure_channel_ctx.lock);
  return new_seq;
}

bool secure_uart_channel_check_recv_seq_number(uint32_t new_seq) {
  rtos_mutex_lock(&secure_channel_ctx.lock);
  bool retval = false;
  if (new_seq > uxc_channel_local_state.recv_sequence_number) {
    uxc_channel_local_state.recv_sequence_number = new_seq;
    retval = true;
  }
  rtos_mutex_unlock(&secure_channel_ctx.lock);
  return retval;
}
