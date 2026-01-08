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
  SECURE_CHANNEL_CONFIRMATION_FAILED,
  SECURE_CHANNEL_ERROR_NO_CONFIRMATION,
} secure_channel_err_t;

typedef enum {
  SECURE_CHANNEL_NONE,
  SECURE_NFC_CHANNEL_CORE,
  SECURE_UART_CHANNEL_UXC,
  SECURE_UART_CHANNEL_CORE,
} secure_channel_type_t;

/**
 * @brief Initialize the NFC secure channel context.
 *
 * Must be called before any other secure_nfc_channel_* functions.
 * Creates the mutex for thread-safe access to the channel context and sets the
 * channel type to SECURE_NFC_CHANNEL_CORE.
 */
void secure_nfc_channel_init(void);

/**
 * @brief Establish a secure channel session with the host over NFC.
 *
 * Generates a key confirmation tag and signature that should be sent to the host for verification.
 *
 * @param[in] pk_host Host's X25519 public key.
 * @param pk_host_len Length of pk_host in bytes.
 * @param[out] pk_device Output buffer for the device's ephemeral public key.
 * @param[in,out] pk_device_len On input, size of pk_device buffer.
 *                              On output, actual public key length.
 * @param[out] exchange_sig Output buffer for signature over the key exchange.
 *                          Must be ECC_SIG_SIZE bytes.
 * @param exchange_sig_len Length of exchange_sig buffer in bytes.
 * @param[out] key_confirmation_tag Output buffer for the confirmation tag.
 *                                  Must be SECURE_CHANNEL_KEY_CONFIRMATION_TAG_LEN bytes.
 * @return SECURE_CHANNEL_OK on success, or SECURE_CHANNEL_FAILED_TO_DERIVE_KEY on failure.
 */
secure_channel_err_t secure_nfc_channel_establish(
  uint8_t* pk_host, uint32_t pk_host_len, uint8_t* pk_device, uint32_t* pk_device_len,
  uint8_t* exchange_sig, uint32_t exchange_sig_len,
  uint8_t key_confirmation_tag[SECURE_CHANNEL_KEY_CONFIRMATION_TAG_LEN]);

/**
 * @brief Encrypt plaintext using AES-GCM with the NFC session send key.
 *
 * Generates a random nonce and outputs the authentication tag.
 * May encrypt in-place (plaintext == ciphertext is allowed).
 *
 * @param[in] plaintext Input data to encrypt.
 * @param[out] ciphertext Output buffer for encrypted data. May be same as plaintext.
 * @param len Length of plaintext/ciphertext in bytes.
 * @param[out] nonce Output buffer for the generated IV. Must be AES_GCM_IV_LENGTH bytes.
 * @param[out] mac Output buffer for the authentication tag. Must be AES_GCM_TAG_LENGTH bytes.
 * @return SECURE_CHANNEL_OK on success, SECURE_CHANNEL_NO_KEY if channel not established,
 *         or SECURE_CHANNEL_CIPHER_FAILED on encryption failure.
 */
secure_channel_err_t secure_nfc_channel_encrypt(uint8_t* plaintext, uint8_t* ciphertext,
                                                uint32_t len, uint8_t nonce[AES_GCM_IV_LENGTH],
                                                uint8_t mac[AES_GCM_TAG_LENGTH]);

/**
 * @brief Decrypt ciphertext using AES-GCM with the NFC session receive key.
 *
 * Verifies the authentication tag before returning plaintext.
 * May NOT decrypt in-place (ciphertext and plaintext must be different buffers).
 *
 * @param[in] ciphertext Input encrypted data.
 * @param[out] plaintext Output buffer for decrypted data. Must NOT be the same address as
 * ciphertext.
 * @param len Length of ciphertext/plaintext in bytes.
 * @param[in] nonce The IV used during encryption. Must be AES_GCM_IV_LENGTH bytes.
 * @param[in] mac The authentication tag to verify. Must be AES_GCM_TAG_LENGTH bytes.
 * @return SECURE_CHANNEL_OK on success, error code on failure.
 */
secure_channel_err_t secure_nfc_channel_decrypt(uint8_t* ciphertext, uint8_t* plaintext,
                                                uint32_t len, uint8_t nonce[AES_GCM_IV_LENGTH],
                                                uint8_t mac[AES_GCM_TAG_LENGTH]);

/**
 * @brief Initialize the UART secure channel context.
 *
 * Must be called before any other secure_uart_channel_* functions.
 * Creates the mutex for thread-safe access to the channel context and sets channel type.
 *
 * @param channel_type The channel type. Must be SECURE_UART_CHANNEL_CORE
 *                     or SECURE_UART_CHANNEL_UXC.
 */
void secure_uart_channel_init(secure_channel_type_t channel_type);

/**
 * @brief Generate or retrieve the local X25519 public key.
 *
 * If keys have not been generated yet, generates a new X25519 keypair.
 * Copies the public key to the output buffer.
 *
 * @param[out] public_key Output buffer for the public key.
 *                        Must be at least EC_PUBKEY_SIZE_X25519 bytes.
 * @param[in,out] pubkey_len On input, size of public_key buffer.
 *                           On output, actual public key length.
 * @return SECURE_CHANNEL_OK on success, or error code on failure.
 */
secure_channel_err_t secure_uart_channel_public_key_init(uint8_t* public_key, uint32_t* pubkey_len);

/**
 * @brief Establish a secure channel session using X25519 key exchange.
 *
 * Performs key exchange with the peer's public key and derives session keys
 * (send, receive, confirmation) from the shared secret. Generates a key confirmation
 * tag and signature that must be sent to the peer for mutual authentication.
 *
 * @param[in] pk_peer Peer's X25519 public key. Required, must not be NULL.
 * @param pk_peer_len Length of pk_peer in bytes.
 * @param[out] pk_device Output buffer for local public key. May be NULL if not needed.
 * @param[in,out] pk_device_len On input, size of pk_device buffer.
 *                              On output, actual public key length.
 * @param[in] exchange_sig Signature over the key exchange. Required, must not be NULL.
 * @param exchange_sig_len Length of exchange_sig in bytes.
 * @param[out] key_confirmation_tag Output buffer for the confirmation tag.
 *                                  Must be SECURE_CHANNEL_KEY_CONFIRMATION_TAG_LEN bytes.
 * @return SECURE_CHANNEL_OK on success, or SECURE_CHANNEL_FAILED_TO_DERIVE_KEY on failure.
 */
secure_channel_err_t secure_uart_channel_establish(uint8_t* pk_peer, uint32_t pk_peer_len,
                                                   uint8_t* pk_device, uint32_t* pk_device_len,
                                                   uint8_t* exchange_sig, uint32_t exchange_sig_len,
                                                   uint8_t* key_confirmation_tag);

/**
 * @brief Encrypt plaintext using AES-GCM with the session send key.
 *
 * Generates a random nonce and outputs the authentication tag.
 * May encrypt in-place (plaintext == ciphertext is allowed).
 *
 * @param[in] plaintext Input data to encrypt.
 * @param[out] ciphertext Output buffer for encrypted data. May be same as plaintext.
 * @param len Length of plaintext/ciphertext in bytes.
 * @param[out] nonce Output buffer for the generated IV. Must be AES_GCM_IV_LENGTH bytes.
 * @param[out] mac Output buffer for the authentication tag. Must be AES_GCM_TAG_LENGTH bytes.
 * @return SECURE_CHANNEL_OK on success, or an error code on failure.
 */
secure_channel_err_t secure_uart_channel_encrypt(uint8_t* plaintext, uint8_t* ciphertext,
                                                 uint32_t len, uint8_t* nonce, uint8_t* mac);

/**
 * @brief Decrypt ciphertext using AES-GCM with the session receive key.
 *
 * Verifies the authentication tag before returning plaintext.
 * May NOT decrypt in-place (ciphertext and plaintext must be different buffers).
 *
 * @param[in] ciphertext Input encrypted data.
 * @param[out] plaintext Output buffer for decrypted data. Must NOT be same as ciphertext.
 * @param len Length of ciphertext/plaintext in bytes.
 * @param[in] nonce The IV used during encryption. Must be AES_GCM_IV_LENGTH bytes.
 * @param[in] mac The authentication tag to verify. Must be AES_GCM_TAG_LENGTH bytes.
 * @return SECURE_CHANNEL_OK on success, SECURE_CHANNEL_CIPHER_FAILED if authentication fails.
 */
secure_channel_err_t secure_uart_channel_decrypt(uint8_t* ciphertext, uint8_t* plaintext,
                                                 uint32_t len, uint8_t* nonce, uint8_t* mac);

/**
 * @brief Verify the peer's key confirmation tag to complete the key exchange.
 *
 * Sets the session as confirmed on success, enabling subsequent encryption/decryption operations.
 *
 * TODO [SECENG-8961]: This function should also verify the signature once certs are added
 *
 * @param[in] received_tag The key confirmation tag received from the peer.
 *                         Must be SECURE_CHANNEL_KEY_CONFIRMATION_TAG_LEN bytes.
 * @return SECURE_CHANNEL_OK if tags match, SECURE_CHANNEL_CONFIRMATION_FAILED otherwise.
 */
secure_channel_err_t secure_uart_channel_confirm_session(uint8_t* received_tag);

/**
 * @brief Check if the UART secure channel session has been confirmed.
 *
 * A session is confirmed after secure_uart_channel_confirm_session() successfully
 * verifies the peer's key confirmation tag (and signature after SECENG-8961 is complete).
 *
 * @return true if the session is confirmed, false otherwise.
 */
bool secure_uart_channel_confirmed(void);
