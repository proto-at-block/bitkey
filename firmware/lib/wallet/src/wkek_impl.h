#pragma once

#include "aes.h"
#include "wkek.h"

#include <stdbool.h>

#define WKEK_PATH ("encrypted-wkek.bin")

EXTERN_VISIBLE_FOR_TESTING(
  uint8_t encrypted_wkek_buffer[AES_256_LENGTH_BYTES + SE_WRAPPED_KEY_OVERHEAD]);
EXTERN_VISIBLE_FOR_TESTING(key_handle_t wkek);

void wkek_init();
int wkek_remove_files();
bool wkek_encrypt(const uint8_t* plaintext, uint8_t* ciphertext, uint32_t size,
                  uint8_t iv_out[AES_GCM_IV_LENGTH], uint8_t tag_out[AES_GCM_TAG_LENGTH]);
bool wkek_decrypt(const uint8_t* ciphertext, uint8_t* plaintext, uint32_t size,
                  uint8_t iv[AES_GCM_IV_LENGTH], uint8_t tag[AES_GCM_TAG_LENGTH]);
