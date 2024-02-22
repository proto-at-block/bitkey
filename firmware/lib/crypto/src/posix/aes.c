#include "aes.h"

#include "crypto_impl.h"

#include <openssl/evp.h>

bool aes_gcm_encrypt(const uint8_t* plaintext, uint8_t* ciphertext, uint32_t length,
                     uint8_t iv[AES_GCM_IV_LENGTH], uint8_t tag[AES_GCM_TAG_LENGTH], uint8_t* aad,
                     uint32_t aad_length, key_handle_t* key) {
  EVP_CIPHER_CTX* ctx;

  int len;

  bool result = false;

  ctx = EVP_CIPHER_CTX_new();
  if (!ctx)
    return false;

  if (EVP_EncryptInit_ex(ctx, EVP_aes_256_gcm(), NULL, NULL, NULL) != OPENSSL_OK) {
    goto out;
  }
  if (EVP_EncryptInit_ex(ctx, NULL, NULL, key->key.bytes, iv) != OPENSSL_OK) {
    goto out;
  }
  if (EVP_EncryptUpdate(ctx, NULL, &len, aad, aad_length) != OPENSSL_OK) {
    goto out;
  }
  if (EVP_EncryptUpdate(ctx, ciphertext, &len, plaintext, length) != OPENSSL_OK) {
    goto out;
  }
  if (EVP_EncryptFinal_ex(ctx, ciphertext + len, &len) != OPENSSL_OK) {
    goto out;
  }

  if (EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_GET_TAG, AES_GCM_TAG_LENGTH, tag) != OPENSSL_OK) {
    goto out;
  }

  result = true;

out:
  EVP_CIPHER_CTX_free(ctx);
  return result;
}

bool aes_gcm_decrypt(const uint8_t* ciphertext, uint8_t* plaintext, uint32_t length,
                     uint8_t iv[AES_GCM_IV_LENGTH], uint8_t tag[AES_GCM_TAG_LENGTH], uint8_t* aad,
                     uint32_t aad_length, key_handle_t* key) {
  EVP_CIPHER_CTX* ctx;
  int len;

  bool result = false;

  ctx = EVP_CIPHER_CTX_new();
  if (!ctx)
    return false;

  if (EVP_DecryptInit_ex(ctx, EVP_aes_256_gcm(), NULL, NULL, NULL) != OPENSSL_OK) {
    goto out;
  }
  if (EVP_DecryptInit_ex(ctx, NULL, NULL, key->key.bytes, iv) != OPENSSL_OK) {
    goto out;
  }
  if (EVP_DecryptUpdate(ctx, NULL, &len, aad, aad_length) != OPENSSL_OK) {
    goto out;
  }
  if (EVP_DecryptUpdate(ctx, plaintext, &len, ciphertext, length) != OPENSSL_OK) {
    goto out;
  }
  if (EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_TAG, AES_GCM_TAG_LENGTH, tag) != OPENSSL_OK) {
    goto out;
  }

  int ret = EVP_DecryptFinal_ex(ctx, plaintext + len, &len);
  result = ret > 0;

out:
  EVP_CIPHER_CTX_free(ctx);
  return result;
}
