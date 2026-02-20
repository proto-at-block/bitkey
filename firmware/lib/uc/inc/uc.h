/**
 * @file
 *
 * @brief UC (UXC COBS)
 */

#pragma once

#include "secutils.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/**
 * @brief Errors returned by the UC public APIs.
 */
typedef enum {
  /**
   * @brief Success.
   */
  UC_ERR_NONE = 0,

  /**
   * @brief Mismatch in CRC.
   */
  UC_ERR_CRC_MISMATCH = 1,

  /**
   * @brief Length of received data is larger than supported.
   */
  UC_ERR_TOO_LARGE = 2,

  /**
   * @brief Not all data written.
   */
  UC_ERR_WR_FAILED = 3,

  /**
   * @brief Invalid argument passed.
   */
  UC_ERR_INVALID_ARG = 4,

  /**
   * @brief COBS decoding failed.
   */
  UC_ERR_DECODE_FAILED = 5,

  /**
   * @brief COBS decoding failed due to buffer being too small.
   */
  UC_ERR_DECODE_TOO_SMALL = 6,

  /**
   * @brief Size mismatch between the decoded data and the message length
   * (including header, encryption and signing).
   */
  UC_ERR_SIZE_MISMATCH = 7,

  /**
   * @brief Retried sending data the maximum amount of times without receiving
   * an ACK.
   */
  UC_ERR_MAX_RETRANSMITS = 8,

  /**
   * @brief Failed to encode proto data.
   */
  UC_ERR_PROTO_ENCODE_FAILED = 9,

  /**
   * @brief Failed to decode proto data.
   */
  UC_ERR_PROTO_DECODE_FAILED = 10,

  /**
   * @brief Failed to lock mutex.
   */
  UC_ERR_MUTEX_FAILED = 11,

  /**
   * @brief Ran out of memory posting to task queue.
   */
  UC_ERR_Q_MAX = 12,

  /**
   * @brief Failure while encrypting message payload.
   *
   */
  UC_ERR_ENCRYPT_FAILED = 13,

  /**
   * @brief Failure when decrypting message payload
   *
   * @note This could be due to an authentication tag mismatch.
   */
  UC_ERR_DECRYPT_FAILED = 14,
} uc_err_t;

/**
 * @brief Callback passed to #uc_send() for sending data.
 *
 * @param context   User-supplied pointer to pass to the callback.
 * @param data      Pointer to the buffer of data to write.
 * @param data_len  Length of the @p data in bytes.
 *
 * @return Number of bytes written.
 *
 * @note If zero bytes are written, then the send will be aborted.
 */
typedef uint32_t (*uc_send_callback_t)(void* context, const uint8_t* data, size_t data_len);

/**
 * @brief Callback to encrypt plaintext using AES-GCM.
 *
 * Generates a random nonce and outputs the authentication tag.
 * May encrypt in-place (plaintext == ciphertext is allowed).
 *
 * The key is not an input because it is handled by the callback.
 *
 * This callback must be thread safe.
 *
 * @param[in] plaintext Input data to encrypt.
 * @param[out] ciphertext Output buffer for encrypted data. May be same as plaintext.
 * @param len Length of plaintext/ciphertext in bytes.
 * @param[in] aad Additional data to be authenticated
 * @param[in] aad_len Length of aad
 * @param[out] nonce Output buffer for the generated IV. Must be AES_GCM_IV_LENGTH bytes.
 * @param[out] mac Output buffer for the authentication tag. Must be AES_GCM_TAG_LENGTH bytes.
 * @return SECURE_TRUE on success, SECURE_FALSE on failure.
 */
typedef secure_bool_t (*uc_gcm_encrypt_callback_t)(uint8_t const* plaintext, uint8_t* ciphertext,
                                                   uint32_t len, uint8_t const* aad,
                                                   uint32_t aad_len, uint8_t* nonce, uint8_t* mac);

/**
 * @brief Callback to decrypt ciphertext using AES-GCM
 *
 * Verifies the authentication tag before returning plaintext.
 *
 * The key is not an input because it is handled by the callback.
 *
 * This callback must be thread safe.
 *
 * @param[in] ciphertext Input encrypted data.
 * @param[out] plaintext Output buffer for decrypted data. Must NOT be same as ciphertext.
 * @param len Length of ciphertext/plaintext in bytes.
 * @param[in] aad Additional data to be authenticated
 * @param[in] aad_len Length of aad
 * @param[in] nonce The IV used during encryption. Must be AES_GCM_IV_LENGTH bytes.
 * @param[in] mac The authentication tag to verify. Must be AES_GCM_TAG_LENGTH bytes.
 * @return SECURE_TRUE on success, SECURE_FALSE on failure.
 */
typedef secure_bool_t (*uc_gcm_decrypt_callback_t)(uint8_t const* ciphertext, uint8_t* plaintext,
                                                   uint32_t len, uint8_t const* aad,
                                                   uint32_t aad_len, uint8_t* nonce, uint8_t* mac);

/**
 * @brief Callback to get the next sequence number for sending encrypted messages.
 *
 * Returns a monotonically increasing sequence number used to prevent replay attacks.
 * The sequence number is included in the AAD during encryption.
 *
 * This callback must be thread safe.
 *
 * @return The next sequence number to use.
 */
typedef uint32_t (*uc_get_send_seq_cb)(void);

/**
 * @brief Callback to validate a received sequence number for replay protection.
 *
 * Checks whether the received sequence number is valid (i.e. greater than previous
 * sequence numbers). Used to detect and reject replayed messages.
 *
 * This callback must be thread safe.
 *
 * @param new_seq The sequence number received in the incoming message.
 * @return true if the sequence number is valid and should be accepted, false otherwise.
 */
typedef bool (*uc_check_recv_seq_cb)(uint32_t new_seq);

/**
 * @brief Cryptographic API callbacks for UC message encryption and replay protection.
 */
typedef struct {
  /** @brief Callback for AES-GCM encryption of outgoing messages. */
  uc_gcm_encrypt_callback_t gcm_encrypt;
  /** @brief Callback for AES-GCM decryption of incoming messages. */
  uc_gcm_decrypt_callback_t gcm_decrypt;
  /** @brief Callback to get the next send sequence number for replay protection. */
  uc_get_send_seq_cb get_send_seq;
  /** @brief Callback to validate received sequence numbers for replay protection. */
  uc_check_recv_seq_cb check_recv_seq;
} uc_crypto_api_t;

/**
 * @brief Initializes the internal state used by the UC library.
 *
 * @note If crypto_api is NULL messages will not be encrypted.
 *
 * @param send_cb     Callback to invoke to send data.
 * @param crypto_api  Cryptographic API callbacks for encryption and replay protection.
 * @param context     User-supplied context pointer.
 */
void uc_init(uc_send_callback_t send_cb, uc_crypto_api_t const* crypto_api, void* context);

/**
 * @brief Performs an idle check for the UC library, sending an ACK if necessary.
 *
 * @param context  Unused.
 */
void uc_idle(void* context);

/**
 * @brief Sends a pure ACK message.
 *
 * @return #uc_err_t.
 */
uc_err_t uc_ack(void);

/**
 * @brief Sends a proto to the companion MCU.
 *
 * @details Upon receipt, the recipient has up to #UC_ACK_TIMEOUT_MS to send
 * a message, otherwise they will send a pure ACK in response.
 *
 * @param proto  Pointer to the protobuf allocated by #uc_alloc_send_proto().
 *
 * @return `true` if proto was sent successfully, otherwise `false`.
 *
 * @note After this method is called, @p proto can no longer be used.
 *
 * @note This message will block up to #UC_RETRANSMIT_TIMEOUT_MS * #UC_RETRANSMIT_MAX_COUNT
 * milliseconds.
 */
bool uc_send(void* proto);

/**
 * @brief Sends a proto to the companion MCU.
 *
 * @details Unlike #uc_send(), the recipient will immediately send an ACK upon
 * receipt of the message. This API allows for high throughput at the
 * expense of more serial transfers being performed, meaning higher CPU load.
 * This API should only be used for time sensitive information exchange.
 *
 * @param proto  Pointer to the protobuf allocated by #uc_alloc_send_proto().
 *
 * @return `true` if proto was sent successfully, otherwise `false`.
 *
 * @note After this method is called, @p proto can no longer be used.
 *
 * @note This message will block up to #UC_RETRANSMIT_TIMEOUT_MS * #UC_RETRANSMIT_MAX_COUNT
 * milliseconds.
 */
bool uc_send_immediate(void* proto);

/**
 * @brief Passes raw data to the UXC library.
 *
 * @param data      Pointer to the received data.
 * @param data_len  Length of the @p data in bytes.
 * @param context   User-supplied context pointer (unused).
 */
void uc_handle_data(const uint8_t* data, uint32_t data_len, void* context);

/**
 * @brief Allocates memory for a UXC proto to send to the companion MCU.
 *
 * @return Pointer to the allocated memory.
 *
 * @note The caller is responsible for free'ing the memory, either by calling
 * #uc_free_send_proto() or by calling #uc_send().
 */
void* uc_alloc_send_proto(void);

/**
 * @brief Frees memory allocated for a UXC send proto.
 *
 * @param proto_buffer  Pointer to the allocated proto memory.
 */
void uc_free_send_proto(void* proto_buffer);

/**
 * @brief Frees memory allocated for a received UXC proto.
 *
 * @param proto_buffer  Pointer to the allocated proto memory.
 *
 * @note This is memory for a proto that is passed to a registered proto
 * route callback.
 */
void uc_free_recv_proto(void* proto_buffer);
