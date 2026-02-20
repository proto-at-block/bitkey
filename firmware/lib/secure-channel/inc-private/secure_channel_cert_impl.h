/**
 * @file
 *
 * @brief Secure Channel Certificate Implementation
 *
 * @details Implementation of the secure channel certificate API.
 *
 * @{
 */

#pragma once

#include "ecc.h"
#include "key_management.h"
#include "secure_channel_cert.h"

// Buffer size for wrapped P-256 ECDSA keys: private (32) + public (64) + SE overhead (28) = 124
// bytes
#define SE_WRAPPED_ECC_P256_KEY_BUFFER_SIZE \
  (ECC_PRIVKEY_SIZE + ECC_PUBKEY_SIZE_ECDSA_UNCOMPRESSED + SE_WRAPPED_KEY_OVERHEAD)

/**
 * @brief Generate a certificate
 * @param desc Certificate descriptor
 * @return true if successful, false otherwise
 */
bool secure_channel_cert_generate_certificate(const secure_channel_cert_desc_t* desc);

/**
 * @brief Format a picocert
 * @param cert Certificate
 * @param privkey Private key
 * @param pubkey Public key
 * @param subject Subject
 * @param subject_size Subject size
 * @param valid_from Valid from
 * @param valid_to Valid to
 * @return true if successful, false otherwise
 */
bool secure_channel_cert_format_picocert(picocert_t* cert, key_handle_t* privkey,
                                         key_handle_t* pubkey, const char* subject,
                                         const size_t subject_size, uint64_t valid_from,
                                         uint64_t valid_to);

/**
 * @brief Write a certificate
 * @param subject Subject
 * @param cert_data Certificate data
 * @return true if successful, false otherwise
 */
bool secure_channel_cert_write_cert(const char* subject,
                                    const secure_channel_cert_data_t* cert_data);

/**
 * @brief Read a certificate
 * @param subject Subject
 * @param cert_data_out Certificate data output
 * @return true if successful, false otherwise
 */
bool secure_channel_cert_load(const char* subject, secure_channel_cert_data_t* cert_data_out);

/**
 * @brief Check if a certificate exists
 * @param subject Subject
 * @return true if exists, false otherwise
 */
bool secure_channel_cert_exists(const char* subject);

/**
 * @brief Format a certificate path
 * @param subject Subject
 * @param path_out Path output
 * @param path_size Path size
 * @return true if successful, false otherwise
 */
bool secure_channel_cert_format_cert_path(const char* subject, char* path_out,
                                          const size_t path_size);

/**
 * @brief Write a key
 * @param subject Subject
 * @param key_buf Key buffer
 * @param key_buf_size Key buffer size
 * @return true if successful, false otherwise
 */
bool secure_channel_cert_write_key(const char* subject, const uint8_t* key_buf,
                                   uint32_t key_buf_size);

/**
 * @brief Read a key
 * @param subject Subject
 * @param key_buf_out Key buffer output
 * @param key_buf_size Key buffer size
 * @return true if successful, false otherwise
 */
bool secure_channel_cert_read_key(const char* subject, uint8_t* key_buf_out, uint32_t key_buf_size);

/**
 * @brief Check if a key exists
 * @param subject Subject
 * @return true if exists, false otherwise
 */
bool secure_channel_cert_key_exists(const char* subject);

/**
 * @brief Format a key path
 * @param subject Subject
 * @param path_out Path output
 * @param path_size Path size
 * @return true if successful, false otherwise
 */
bool secure_channel_cert_format_key_path(const char* subject, char* path_out,
                                         const size_t path_size);

/**
 * @brief Clear a certificate and key file
 * @param subject Subject
 * @details Clears the certificate and key files for the given subject. This allows for recovery
 * from errors during init.
 * @return true if successful, false otherwise
 */
bool secure_channel_cert_clear_cert_and_key_files(const char* subject);

/**
 * @brief Verify a self-signed picocert signature
 * @param cert Certificate
 * @return true if successful, false otherwise
 */
bool secure_channel_cert_verify_self_signed_picocert_signature(const picocert_t* cert);
/** @} */
