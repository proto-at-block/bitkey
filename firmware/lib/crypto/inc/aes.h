#pragma once

#include "key_management.h"

#include <stdbool.h>
#include <stdint.h>

#define AES_CMAC_LENGTH      (16)
#define AES_CBC_IV_LENGTH    (16)
#define AES_GCM_IV_LENGTH    (12)
#define AES_GCM_TAG_LENGTH   (16)
#define AES_128_LENGTH_BYTES (16)
#define AES_256_LENGTH_BYTES (32)
#define AES_GCM_OVERHEAD     (AES_GCM_IV_LENGTH + AES_GCM_TAG_LENGTH)

// May encrypt in-place.
bool aes_gcm_encrypt(const uint8_t* plaintext, uint8_t* ciphertext, uint32_t length,
                     uint8_t iv[AES_GCM_IV_LENGTH], uint8_t tag[AES_GCM_TAG_LENGTH], uint8_t* aad,
                     uint32_t aad_length, key_handle_t* key);

// May NOT decrypt in-place.
bool aes_gcm_decrypt(const uint8_t* ciphertext, uint8_t* plaintext, uint32_t length,
                     uint8_t iv[AES_GCM_IV_LENGTH], uint8_t tag[AES_GCM_TAG_LENGTH], uint8_t* aad,
                     uint32_t aad_length, key_handle_t* key);

bool aes_cmac(uint8_t* data, uint32_t data_length, uint8_t mac[AES_CMAC_LENGTH], key_handle_t* key);

bool aes_cbc_encrypt(uint8_t* plaintext, uint8_t* ciphertext, uint32_t length,
                     uint8_t iv[AES_CBC_IV_LENGTH], key_handle_t* key);
bool aes_cbc_decrypt(uint8_t* ciphertext, uint8_t* plaintext, uint32_t length,
                     uint8_t iv[AES_CBC_IV_LENGTH], key_handle_t* key);

// This API only allows input sizes of 128 bits to discourage usage of ECB mode directly.
bool aes_one_block_encrypt(uint8_t* pt_block, uint8_t* ct_block, uint32_t block_length,
                           key_handle_t* key);
bool aes_one_block_decrypt(uint8_t* ct_block, uint8_t* pt_block, uint32_t block_length,
                           key_handle_t* key);
