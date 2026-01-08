#include "aes.h"

#include "mcu_aes.h"

#include <string.h>

_Static_assert(MCU_AES_256_GCM_KEY_SIZE == AES_256_LENGTH_BYTES, "AES key size must match MCU.");
_Static_assert(MCU_AES_256_GCM_IV_SIZE == AES_GCM_IV_LENGTH, "AES iv size must match MCU.");
_Static_assert(MCU_AES_256_GCM_TAG_SIZE == AES_GCM_TAG_LENGTH, "AES tag size must match MCU.");

bool aes_gcm_encrypt(const uint8_t* plaintext, uint8_t* ciphertext, uint32_t ciphertext_length,
                     uint8_t const* iv, uint8_t* tag, uint8_t const* aad, uint32_t aad_length,
                     key_handle_t* key) {
  // Validate input parameters
  if (plaintext == NULL || ciphertext == NULL || iv == NULL || tag == NULL || key == NULL) {
    return false;
  }

  // AAD is optional, but if aad_length is non-zero, aad must be non-NULL
  if (aad_length > 0 && aad == NULL) {
    return false;
  }

  if (key->storage_type != KEY_STORAGE_EXTERNAL_PLAINTEXT || key->key.bytes == NULL ||
      key->key.size < AES_256_LENGTH_BYTES) {
    return false;
  }

  // Call MCU-level AES-GCM encrypt function
  mcu_err_t result =
    mcu_aes_gcm_encrypt(key->key.bytes, key->key.size, iv, aad, aad_length, plaintext,
                        ciphertext_length, ciphertext, ciphertext_length, tag, AES_GCM_TAG_LENGTH);

  return (result == MCU_ERROR_OK);
}

bool aes_gcm_decrypt(const uint8_t* ciphertext, uint8_t* plaintext, uint32_t plaintext_length,
                     uint8_t const* iv, uint8_t* tag, uint8_t const* aad, uint32_t aad_length,
                     key_handle_t* key) {
  // Validate input parameters
  if (ciphertext == NULL || plaintext == NULL || iv == NULL || tag == NULL || key == NULL) {
    return false;
  }

  // AAD is optional, but if aad_length is non-zero, aad must be non-NULL
  if (aad_length > 0 && aad == NULL) {
    return false;
  }

  if (key->storage_type != KEY_STORAGE_EXTERNAL_PLAINTEXT || key->key.bytes == NULL ||
      key->key.size < AES_256_LENGTH_BYTES) {
    return false;
  }

  // Call MCU-level AES-GCM decrypt function
  mcu_err_t result =
    mcu_aes_gcm_decrypt(key->key.bytes, key->key.size, iv, aad, aad_length, ciphertext,
                        plaintext_length, plaintext, plaintext_length, tag, AES_GCM_TAG_LENGTH);

  return (result == MCU_ERROR_OK);
}
