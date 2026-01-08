#include "secure_channel_common.h"

#include "hash.h"
#include "key_exchange.h"
#include "log.h"
#include "secure_channel.h"
#include "secure_rng.h"
#include "secutils.h"

#define CORE_TO_HOST_CONFIRM     "KEYCONFIRM-V1"
#define CORE_TO_HOST_CONFIRM_LEN (sizeof(CORE_TO_HOST_CONFIRM) - 1)

#define UXC_TO_CORE_CONFIRM     "UART-UXC-CONFIRM-V1"
#define UXC_TO_CORE_CONFIRM_LEN (sizeof(UXC_TO_CORE_CONFIRM) - 1)

#define CORE_TO_UXC_CONFIRM     "UART-CORE-CONFIRM-V1"
#define CORE_TO_UXC_CONFIRM_LEN (sizeof(CORE_TO_UXC_CONFIRM) - 1)

static uint8_t const* confirmation_message_from_type(secure_channel_type_t channel_type,
                                                     uint32_t* message_len) {
  switch (channel_type) {
    case SECURE_NFC_CHANNEL_CORE: {
      *message_len = CORE_TO_HOST_CONFIRM_LEN;
      return (const uint8_t*)CORE_TO_HOST_CONFIRM;
    }
    case SECURE_UART_CHANNEL_UXC: {
      *message_len = UXC_TO_CORE_CONFIRM_LEN;
      return (const uint8_t*)UXC_TO_CORE_CONFIRM;
    }
    case SECURE_UART_CHANNEL_CORE: {
      *message_len = CORE_TO_UXC_CONFIRM_LEN;
      return (const uint8_t*)CORE_TO_UXC_CONFIRM;
    }
    default: {
      ASSERT(false);
      return NULL;
    }
  }
}

secure_channel_err_t secure_channel_compute_confirmation(secure_channel_type_t channel_type,
                                                         key_handle_t* conf_key,
                                                         uint8_t* confirmation_tag) {
  ASSERT(channel_type != SECURE_CHANNEL_NONE);
  ASSERT(conf_key != NULL);
  ASSERT(confirmation_tag != NULL);
  uint32_t confirmation_message_len = 0;
  uint8_t const* confirmation_message =
    confirmation_message_from_type(channel_type, &confirmation_message_len);

  uint8_t hmac_result[SHA256_DIGEST_SIZE] = {0};
  if (!crypto_hmac((uint8_t const*)confirmation_message, confirmation_message_len, conf_key,
                   hmac_result, sizeof(hmac_result), ALG_SHA256)) {
    return SECURE_CHANNEL_FAILED_TO_DERIVE_KEY;
  }

  memcpy(confirmation_tag, hmac_result, SECURE_CHANNEL_KEY_CONFIRMATION_TAG_LEN);

  return SECURE_CHANNEL_OK;
}

secure_channel_err_t secure_channel_establish_impl(secure_channel_ctx_t* secure_channel_ctx,
                                                   uint8_t* pk_host, uint32_t pk_host_len,
                                                   key_handle_t* sk_device, key_handle_t* pk_device,
                                                   uint8_t* exchange_sig,
                                                   uint32_t exchange_sig_len) {
  ASSERT(secure_channel_ctx && pk_host && sk_device && pk_device && exchange_sig);

  // Always establish new keys, even if we already have one. The other party may have lost theirs.
  memzero(secure_channel_ctx->send_key_buf, sizeof(secure_channel_ctx->send_key_buf));
  memzero(secure_channel_ctx->recv_key_buf, sizeof(secure_channel_ctx->recv_key_buf));
  secure_channel_ctx->established = false;

  if (pk_device->key.size > SECURE_CHANNEL_PUBKEY_MAX_LEN) {
    LOGE("pubkey may be at most %d bytes", SECURE_CHANNEL_PUBKEY_MAX_LEN);
    return SECURE_CHANNEL_FAILED_TO_DERIVE_KEY;
  }

  crypto_key_exchange_ctx_t key_exchange_ctx = {
    .pk_peer = pk_host,
    .pk_peer_len = pk_host_len,
    .sk_us = sk_device,
    .pk_us = pk_device,
    .exchange_sig = exchange_sig,
    .exchange_sig_len = exchange_sig_len,
  };

  // The original core-to-host secure channel uses the efr32 serial number to compute labels
  if (secure_channel_ctx->channel_type == SECURE_NFC_CHANNEL_CORE) {
    key_exchange_ctx.use_sn = true;
  } else {
    key_exchange_ctx.use_sn = false;
  }

  crypto_key_exchange_derived_key_material_t key_material = {
    .conf_key = secure_channel_ctx->conf_key_buf,
  };

  // send/recv keys are from the perspective of core so they should be reverse on UXC
  switch (secure_channel_ctx->channel_type) {
    case SECURE_UART_CHANNEL_CORE:
    case SECURE_NFC_CHANNEL_CORE: {
      key_material.send_key = secure_channel_ctx->send_key_buf;
      key_material.recv_key = secure_channel_ctx->recv_key_buf;
      break;
    }
    case SECURE_UART_CHANNEL_UXC: {
      key_material.send_key = secure_channel_ctx->recv_key_buf;
      key_material.recv_key = secure_channel_ctx->send_key_buf;
      break;
    }
    default:
      // Unknown or unset channel type.
      ASSERT(false);
  }

  if (!crypto_key_exchange(&key_exchange_ctx, &key_material)) {
    LOGE("crypto_key_exchange failed");
    return SECURE_CHANNEL_FAILED_TO_DERIVE_KEY;
  }

  secure_channel_ctx->established = true;

  return SECURE_CHANNEL_OK;
}

secure_channel_err_t secure_channel_cipher(secure_channel_ctx_t* secure_channel_ctx,
                                           secure_channel_cipher_op_t op, uint8_t* data_in,
                                           uint8_t* data_out, uint32_t data_len, uint8_t* nonce,
                                           uint8_t* mac) {
  ASSERT(secure_channel_ctx);
  ASSERT(data_in && data_out && nonce && mac);

  rtos_mutex_lock(&secure_channel_ctx->lock);

  secure_channel_err_t result = SECURE_CHANNEL_CIPHER_FAILED;

  if (!secure_channel_ctx->established) {
    LOGE("secure channel not established");
    result = SECURE_CHANNEL_NO_KEY;
    goto out;
  }

  bool ok = false;
  switch (op) {
    case SECURE_CHANNEL_ENCRYPT: {
      if (!crypto_random(nonce, AES_GCM_IV_LENGTH)) {
        goto out;
      }
      ok = aes_gcm_encrypt(data_in, data_out, data_len, nonce, mac, NULL, 0,
                           &secure_channel_ctx->session_send_key);
      break;
    }
    case SECURE_CHANNEL_DECRYPT: {
      ok = aes_gcm_decrypt(data_in, data_out, data_len, nonce, mac, NULL, 0,
                           &secure_channel_ctx->session_recv_key);
      break;
    }
    default: {
      ASSERT(false);
      break;
    }
  }

  result = ok ? SECURE_CHANNEL_OK : SECURE_CHANNEL_CIPHER_FAILED;

out:
  rtos_mutex_unlock(&secure_channel_ctx->lock);
  return result;
}
