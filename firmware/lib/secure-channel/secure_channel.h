#pragma once

#include "aes.h"
#include "key_management.h"

#include <stdbool.h>
#include <stdint.h>

#define SECURE_CHANNEL_PROTOCOL_VERSION (1)

#define SECURE_CHANNEL_PUBKEY_MAX_LEN           (64)
#define SECURE_CHANNEL_SESSION_KEY_LEN          (32)
#define SECURE_CHANNEL_KEY_CONFIRMATION_TAG_LEN (16)

typedef enum {
  SECURE_CHANNEL_OK = 0,
  SECURE_CHANNEL_NO_KEY,
  SECURE_CHANNEL_FAILED_TO_DERIVE_KEY,
  SECURE_CHANNEL_CIPHER_FAILED,
} secure_channel_err_t;

void secure_channel_init(void);

secure_channel_err_t secure_channel_establish(
  uint8_t* pk_host, uint32_t pk_host_len, uint8_t* pk_device, uint32_t* pk_device_len,
  uint8_t* exchange_sig, uint32_t exchange_sig_len,
  uint8_t key_confirmation_tag[SECURE_CHANNEL_KEY_CONFIRMATION_TAG_LEN]);

// IMPORTANT: Nonce will be randomly generated and copied into the nonce parameter.
// May encrypt in-place.
secure_channel_err_t secure_channel_encrypt(uint8_t* plaintext, uint8_t* ciphertext, uint32_t len,
                                            uint8_t nonce[AES_GCM_IV_LENGTH],
                                            uint8_t mac[AES_GCM_TAG_LENGTH]);

// May NOT decrypt in-place.
secure_channel_err_t secure_channel_decrypt(uint8_t* ciphertext, uint8_t* plaintext, uint32_t len,
                                            uint8_t nonce[AES_GCM_IV_LENGTH],
                                            uint8_t mac[AES_GCM_TAG_LENGTH]);
