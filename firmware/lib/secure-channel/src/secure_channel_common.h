/**
 * @file secure_channel_common.h
 * @brief Internal implementation details for secure channel communication
 *
 * This file contains internal data structures and functions used by the
 * secure channel implementation. It should not be included directly by
 * application code; use secure_channel.h instead.
 */

#pragma once

#include "aes.h"
#include "key_management.h"
#include "rtos.h"
#include "secure_channel.h"

#include <stdint.h>

/**
 * @brief Secure channel context containing session state and keys
 *
 * This structure maintains the state of an established secure channel,
 * including the derived session keys and synchronization primitives.
 * Access to this structure should be protected by the embedded mutex.
 */
typedef struct {
  secure_channel_type_t channel_type;         /**< Session type */
  uint8_t send_key_buf[AES_256_LENGTH_BYTES]; /**< Buffer for sending encryption key */
  uint8_t recv_key_buf[AES_256_LENGTH_BYTES]; /**< Buffer for receiving decryption key */
  uint8_t conf_key_buf[AES_256_LENGTH_BYTES]; /**< Buffer for key confirmation tag */
  key_handle_t session_send_key;              /**< Handle for sending key */
  key_handle_t session_recv_key;              /**< Handle for receiving key */
  key_handle_t session_conf_key;              /**< Handle for confirmation key */
  bool established;                           /**< True if channel is established */
  rtos_mutex_t lock;                          /**< Mutex for thread-safe access */
} secure_channel_ctx_t;

typedef enum {
  SECURE_CHANNEL_NO_OP,
  SECURE_CHANNEL_ENCRYPT,
  SECURE_CHANNEL_DECRYPT,
} secure_channel_cipher_op_t;

/**
 * @brief Internal implementation of secure_nfc_channel_establish
 *
 * @note This function takes a secure channel context to allow multiple channels to share the same
 * logic.
 */
secure_channel_err_t secure_channel_establish_impl(secure_channel_ctx_t* secure_channel_ctx,
                                                   uint8_t* pk_host, uint32_t pk_host_len,
                                                   key_handle_t* sk_device, key_handle_t* pk_device,
                                                   uint8_t* exchange_sig,
                                                   uint32_t exchange_sig_len);

/**
 * @brief Internal function to encrypt or decrypt data using the secure channel session keys
 *
 * @note This function takes a secure channel context to allow multiple channels to share the same
 * logic.
 */

secure_channel_err_t secure_channel_cipher(secure_channel_ctx_t* secure_channel_ctx,
                                           secure_channel_cipher_op_t op, uint8_t* data_in,
                                           uint8_t* data_out, uint32_t data_len, uint8_t* nonce,
                                           uint8_t* mac);

secure_channel_err_t secure_channel_compute_confirmation(secure_channel_type_t channel_type,
                                                         key_handle_t* conf_key,
                                                         uint8_t* confirmation_tag);
