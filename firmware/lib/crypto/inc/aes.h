/**
 * @file aes.h
 * @brief AES encryption and decryption functions.
 *
 * Provides AES-GCM, AES-CBC, AES-CMAC, and single-block AES operations.
 * All functions use key_handle_t for key management abstraction.
 */

#pragma once

#include "key_management.h"

#include <stdbool.h>
#include <stdint.h>

/** @brief Length of AES-CMAC output in bytes */
#define AES_CMAC_LENGTH (16)
/** @brief Length of AES-CBC initialization vector in bytes */
#define AES_CBC_IV_LENGTH (16)
/** @brief Length of AES-GCM initialization vector in bytes */
#define AES_GCM_IV_LENGTH (12)
/** @brief Length of AES-GCM authentication tag in bytes */
#define AES_GCM_TAG_LENGTH (16)
/** @brief Length of AES-128 key in bytes */
#define AES_128_LENGTH_BYTES (16)
/** @brief Length of AES-256 key in bytes */
#define AES_256_LENGTH_BYTES (32)
/** @brief Total overhead for AES-GCM (IV + tag) */
#define AES_GCM_OVERHEAD (AES_GCM_IV_LENGTH + AES_GCM_TAG_LENGTH)

/**
 * @brief Encrypt data using AES-GCM (Galois/Counter Mode).
 *
 * Encrypts plaintext and produces an authentication tag. Supports additional
 * authenticated data (AAD) that is authenticated but not encrypted.
 * May encrypt in-place (plaintext == ciphertext is allowed).
 *
 * @param[in] plaintext Input data to encrypt.
 * @param[out] ciphertext Output buffer for encrypted data. May be same as plaintext.
 * @param length Length of plaintext/ciphertext in bytes.
 * @param[in] iv Initialization vector. Must be AES_GCM_IV_LENGTH bytes.
 * @param[out] tag Output buffer for authentication tag. Must be AES_GCM_TAG_LENGTH bytes.
 * @param[in] aad Additional authenticated data. May be NULL if aad_length is 0.
 * @param aad_length Length of AAD in bytes.
 * @param[in] key Key handle for encryption key.
 * @return true on success, false on failure.
 */
bool aes_gcm_encrypt(const uint8_t* plaintext, uint8_t* ciphertext, uint32_t length,
                     uint8_t const iv[AES_GCM_IV_LENGTH], uint8_t tag[AES_GCM_TAG_LENGTH],
                     uint8_t const* aad, uint32_t aad_length, key_handle_t* key);

/**
 * @brief Decrypt data using AES-GCM (Galois/Counter Mode).
 *
 * Decrypts ciphertext and verifies the authentication tag. Supports additional
 * authenticated data (AAD) that was authenticated during encryption.
 * May NOT decrypt in-place (ciphertext and plaintext must be different buffers).
 *
 * @param[in] ciphertext Input encrypted data.
 * @param[out] plaintext Output buffer for decrypted data. Must NOT be same as ciphertext.
 * @param length Length of ciphertext/plaintext in bytes.
 * @param[in] iv Initialization vector used during encryption. Must be AES_GCM_IV_LENGTH bytes.
 * @param[in] tag Authentication tag to verify. Must be AES_GCM_TAG_LENGTH bytes.
 * @param[in] aad Additional authenticated data. May be NULL if aad_length is 0.
 * @param aad_length Length of AAD in bytes.
 * @param[in] key Key handle for decryption key.
 * @return true on success (tag verified), false on failure or authentication error.
 */
bool aes_gcm_decrypt(const uint8_t* ciphertext, uint8_t* plaintext, uint32_t length,
                     uint8_t const iv[AES_GCM_IV_LENGTH], uint8_t tag[AES_GCM_TAG_LENGTH],
                     uint8_t const* aad, uint32_t aad_length, key_handle_t* key);

/**
 * @brief Compute AES-CMAC (Cipher-based Message Authentication Code).
 *
 * Generates a 128-bit authentication tag for the input data using AES-CMAC.
 *
 * @param[in] data Input data to authenticate.
 * @param data_length Length of data in bytes.
 * @param[out] mac Output buffer for MAC. Must be AES_CMAC_LENGTH bytes.
 * @param[in] key Key handle for CMAC key.
 * @return true on success, false on failure.
 */
bool aes_cmac(uint8_t* data, uint32_t data_length, uint8_t mac[AES_CMAC_LENGTH], key_handle_t* key);

/**
 * @brief Encrypt data using AES-CBC (Cipher Block Chaining) mode.
 *
 * @param[in] plaintext Input data to encrypt. Must be a multiple of 16 bytes.
 * @param[out] ciphertext Output buffer for encrypted data.
 * @param length Length of plaintext/ciphertext in bytes. Must be a multiple of 16.
 * @param[in,out] iv Initialization vector. Must be AES_CBC_IV_LENGTH bytes.
 *                   May be modified during operation.
 * @param[in] key Key handle for encryption key.
 * @return true on success, false on failure.
 */
bool aes_cbc_encrypt(uint8_t* plaintext, uint8_t* ciphertext, uint32_t length,
                     uint8_t iv[AES_CBC_IV_LENGTH], key_handle_t* key);

/**
 * @brief Decrypt data using AES-CBC (Cipher Block Chaining) mode.
 *
 * @param[in] ciphertext Input encrypted data. Must be a multiple of 16 bytes.
 * @param[out] plaintext Output buffer for decrypted data.
 * @param length Length of ciphertext/plaintext in bytes. Must be a multiple of 16.
 * @param[in,out] iv Initialization vector used during encryption. Must be AES_CBC_IV_LENGTH bytes.
 *                   May be modified during operation.
 * @param[in] key Key handle for decryption key.
 * @return true on success, false on failure.
 */
bool aes_cbc_decrypt(uint8_t* ciphertext, uint8_t* plaintext, uint32_t length,
                     uint8_t iv[AES_CBC_IV_LENGTH], key_handle_t* key);

/**
 * @brief Encrypt a single 128-bit block using AES-ECB.
 *
 * @warning ECB mode is insecure for most use cases. This API intentionally
 *          restricts input to a single block to discourage direct ECB usage.
 *          Use only when building higher-level constructions.
 *
 * @param[in] pt_block Input plaintext block.
 * @param[out] ct_block Output ciphertext block.
 * @param block_length Length of block in bytes. Must be 16.
 * @param[in] key Key handle for encryption key.
 * @return true on success, false on failure.
 */
bool aes_one_block_encrypt(uint8_t* pt_block, uint8_t* ct_block, uint32_t block_length,
                           key_handle_t* key);

/**
 * @brief Decrypt a single 128-bit block using AES-ECB.
 *
 * @warning ECB mode is insecure for most use cases. This API intentionally
 *          restricts input to a single block to discourage direct ECB usage.
 *          Use only when building higher-level constructions.
 *
 * @param[in] ct_block Input ciphertext block.
 * @param[out] pt_block Output plaintext block.
 * @param block_length Length of block in bytes. Must be 16.
 * @param[in] key Key handle for decryption key.
 * @return true on success, false on failure.
 */
bool aes_one_block_decrypt(uint8_t* ct_block, uint8_t* pt_block, uint32_t block_length,
                           key_handle_t* key);
