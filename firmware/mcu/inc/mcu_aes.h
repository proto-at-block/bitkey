/**
 * @file mcu_aes.h
 * @brief AES encryption and decryption functions.
 */

#pragma once

#include "mcu.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define MCU_AES_256_GCM_KEY_SIZE (32)
#define MCU_AES_256_GCM_IV_SIZE  (12)
#define MCU_AES_256_GCM_TAG_SIZE (16)

/**
 * @brief AES encryption and decryption functions.
 */

/**
 * @brief Initializes the AES module.
 */
void mcu_aes_init(void);

/**
 * @brief Encrypts a block of data using AES-256-GCM.
 * @param[in] key The 256-bit key to use for encryption.
 * @param[in] key_size The size of the key (must be MCU_AES_256_GCM_KEY_SIZE)
 * @param[in] iv The 96-bit initialization vector.
 * @param[in] aad Additional authenticated data (can be NULL if aad_size is 0).
 * @param[in] aad_size The size of the additional authenticated data in bytes.
 * @param[in] input The input data to encrypt.
 * @param[in] input_size The size of the input data in bytes.
 * @param[out] output The output buffer to store the encrypted data.
 * @param[in] output_size The size of the output buffer in bytes.
 * @param[out] tag The output buffer to store the authentication tag.
 * @param[in] tag_size The size of the tag buffer in bytes.
 * @return #MCU_ERROR_OK on success, #MCU_ERROR_PARAMETER if parameters are invalid,
 *         #MCU_ERROR_TIMEOUT if operation times out.
 */
mcu_err_t mcu_aes_gcm_encrypt(const uint8_t* key, size_t key_size, const uint8_t* iv,
                              const uint8_t* aad, size_t aad_size, const uint8_t* input,
                              size_t input_size, uint8_t* output, size_t output_size, uint8_t* tag,
                              size_t tag_size);

/**
 * @brief Decrypts a block of data using AES-256-GCM.
 * @param[in] key The 256-bit key to use for decryption.
 * @param[in] key_size The size of the key (must be MCU_AES_256_GCM_KEY_SIZE)
 * @param[in] iv The 96-bit initialization vector.
 * @param[in] aad Additional authenticated data (can be NULL if aad_size is 0).
 * @param[in] aad_size The size of the additional authenticated data in bytes.
 * @param[in] input The input data to decrypt.
 * @param[in] input_size The size of the input data in bytes.
 * @param[out] output The output buffer to store the decrypted data.
 * @param[in] output_size The size of the output buffer in bytes.
 * @param[in] tag The authentication tag to verify.
 * @param[in] tag_size The size of the authentication tag in bytes.
 * @return #MCU_ERROR_OK on success, #MCU_ERROR_PARAMETER if parameters are invalid or
 *         authentication fails, #MCU_ERROR_TIMEOUT if operation times out.
 */
mcu_err_t mcu_aes_gcm_decrypt(const uint8_t* key, size_t key_size, const uint8_t* iv,
                              const uint8_t* aad, size_t aad_size, const uint8_t* input,
                              size_t input_size, uint8_t* output, size_t output_size,
                              const uint8_t* tag, size_t tag_size);
