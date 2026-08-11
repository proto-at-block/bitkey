/**
 * @file secure_engine.c
 * @brief POSIX SE emulation layer for wrapped key handling.
 *
 * This module provides SE API compatibility for POSIX builds, enabling
 * wrapped keys to be unwrapped transparently before use in crypto operations.
 * On EFR32, the hardware SE handles this automatically; this implementation
 * provides the same behavior using software AES.
 */

#include "secure_engine.h"

#include "aes.h"
#include "crypto_impl.h"
#include "secure_rng.h"

#include <openssl/core_names.h>
#include <openssl/ec.h>
#include <openssl/ecdsa.h>
#include <openssl/evp.h>
#include <openssl/hmac.h>
#include <openssl/kdf.h>
#include <openssl/sha.h>

#include <stdlib.h>
#include <string.h>

// Suppress OpenSSL 3.x deprecation warnings for EC_KEY functions
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wdeprecated-declarations"

// The fake SE KEK used for wrapping/unwrapping keys in POSIX builds.
// Must match the key used in key_management.c for generate_key().
static uint8_t fake_se_kek_buf[AES_256_LENGTH_BYTES] = {
  1, 2, 3, 4, 5, 6, 7, 8, 1, 2, 3, 4, 5, 6, 8, 1, 2, 3, 4, 5, 6, 7, 8, 1, 2, 3, 4, 5, 6, 8,
};

/**
 * @brief Unwrap a key that was wrapped with the fake SE KEK.
 *
 * Wrapped key format: [IV (12 bytes)][ciphertext (key_size)][tag (16 bytes)]
 *
 * @param wrapped_key Pointer to the wrapped key blob
 * @param wrapped_size Size of the wrapped key blob
 * @param unwrapped_key Output buffer for the unwrapped key
 * @param key_size Expected size of the unwrapped key
 * @return true on success, false on failure
 */
static bool se_unwrap_key(const uint8_t* wrapped_key, size_t wrapped_size, uint8_t* unwrapped_key,
                          size_t key_size) {
  if (wrapped_size != key_size + SE_WRAPPED_KEY_OVERHEAD) {
    return false;
  }

  const uint8_t* iv = wrapped_key;
  const uint8_t* ciphertext = wrapped_key + AES_GCM_IV_LENGTH;
  const uint8_t* tag = wrapped_key + AES_GCM_IV_LENGTH + key_size;

  EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();
  if (!ctx) {
    return false;
  }

  bool result = false;
  int len;

  if (EVP_DecryptInit_ex(ctx, EVP_aes_256_gcm(), NULL, NULL, NULL) != OPENSSL_OK) {
    goto out;
  }
  if (EVP_DecryptInit_ex(ctx, NULL, NULL, fake_se_kek_buf, iv) != OPENSSL_OK) {
    goto out;
  }
  if (EVP_DecryptUpdate(ctx, unwrapped_key, &len, ciphertext, key_size) != OPENSSL_OK) {
    goto out;
  }
  if (EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_TAG, AES_GCM_TAG_LENGTH, (void*)tag) !=
      OPENSSL_OK) {
    goto out;
  }
  int ret = EVP_DecryptFinal_ex(ctx, unwrapped_key + len, &len);
  result = ret > 0;

out:
  EVP_CIPHER_CTX_free(ctx);
  return result;
}

/**
 * @brief Get the raw key size from a key descriptor.
 */
static size_t get_key_size(const sl_se_key_descriptor_t* key) {
  switch (key->type) {
    case SL_SE_KEY_TYPE_AES_128:
      return AES_128_LENGTH_BYTES;
    case SL_SE_KEY_TYPE_AES_256:
      return AES_256_LENGTH_BYTES;
    default:
      if (key->size > 0) {
        return key->size;
      }
      return 0;
  }
}

/**
 * @brief Get a usable key buffer, unwrapping if necessary.
 *
 * If the key is stored as wrapped, this function unwraps it into the provided
 * buffer. If plaintext, it simply returns the pointer to the key data.
 *
 * @param key Key descriptor
 * @param unwrap_buf Buffer for unwrapped key (must be at least key_size bytes)
 * @param key_size Size of the key
 * @param out_key_ptr Output: pointer to usable key data
 * @return true on success, false on failure
 */
static bool get_usable_key(const sl_se_key_descriptor_t* key, uint8_t* unwrap_buf, size_t key_size,
                           const uint8_t** out_key_ptr) {
  if (key->storage.method == SL_SE_KEY_STORAGE_EXTERNAL_WRAPPED) {
    if (!se_unwrap_key(key->storage.location.buffer.pointer, key->storage.location.buffer.size,
                       unwrap_buf, key_size)) {
      return false;
    }
    *out_key_ptr = unwrap_buf;
  } else {
    *out_key_ptr = key->storage.location.buffer.pointer;
  }
  return true;
}

sl_status_t sl_se_init_command_context(sl_se_command_context_t* cmd_ctx) {
  if (cmd_ctx == NULL) {
    return SL_STATUS_INVALID_PARAMETER;
  }
  memset(cmd_ctx, 0, sizeof(*cmd_ctx));
  return SL_STATUS_OK;
}

sl_status_t se_aes_gcm(sl_se_command_context_t* cmd_ctx, const sl_se_key_descriptor_t* key,
                       sl_se_cipher_operation_t mode, size_t length,
                       uint8_t const iv[SE_AES_GCM_IV_LENGTH], const unsigned char* aad,
                       size_t aad_length, const unsigned char* input, unsigned char* output,
                       uint8_t tag[SE_AES_GCM_TAG_LENGTH]) {
  (void)cmd_ctx;

  if (key == NULL || iv == NULL || tag == NULL || ((aad_length > 0) && (aad == NULL)) ||
      ((length > 0) && (input == NULL || output == NULL))) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  size_t key_size = get_key_size(key);
  if (key_size == 0) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  uint8_t unwrapped_key[AES_256_LENGTH_BYTES];
  const uint8_t* key_ptr;
  if (!get_usable_key(key, unwrapped_key, key_size, &key_ptr)) {
    return SL_STATUS_FAIL;
  }

  EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();
  if (!ctx) {
    return SL_STATUS_FAIL;
  }

  sl_status_t result = SL_STATUS_FAIL;
  int len;
  const EVP_CIPHER* cipher =
    (key_size == AES_128_LENGTH_BYTES) ? EVP_aes_128_gcm() : EVP_aes_256_gcm();

  if (mode == SL_SE_ENCRYPT) {
    if (EVP_EncryptInit_ex(ctx, cipher, NULL, NULL, NULL) != OPENSSL_OK) {
      goto out;
    }
    if (EVP_EncryptInit_ex(ctx, NULL, NULL, key_ptr, iv) != OPENSSL_OK) {
      goto out;
    }
    if (aad_length > 0) {
      if (EVP_EncryptUpdate(ctx, NULL, &len, aad, aad_length) != OPENSSL_OK) {
        goto out;
      }
    }
    if (length > 0) {
      if (EVP_EncryptUpdate(ctx, output, &len, input, length) != OPENSSL_OK) {
        goto out;
      }
    }
    if (EVP_EncryptFinal_ex(ctx, output + len, &len) != OPENSSL_OK) {
      goto out;
    }
    if (EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_GET_TAG, SE_AES_GCM_TAG_LENGTH, tag) != OPENSSL_OK) {
      goto out;
    }
    result = SL_STATUS_OK;
  } else {
    if (EVP_DecryptInit_ex(ctx, cipher, NULL, NULL, NULL) != OPENSSL_OK) {
      goto out;
    }
    if (EVP_DecryptInit_ex(ctx, NULL, NULL, key_ptr, iv) != OPENSSL_OK) {
      goto out;
    }
    if (aad_length > 0) {
      if (EVP_DecryptUpdate(ctx, NULL, &len, aad, aad_length) != OPENSSL_OK) {
        goto out;
      }
    }
    if (length > 0) {
      if (EVP_DecryptUpdate(ctx, output, &len, input, length) != OPENSSL_OK) {
        goto out;
      }
    }
    if (EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_TAG, SE_AES_GCM_TAG_LENGTH, tag) != OPENSSL_OK) {
      goto out;
    }
    int ret = EVP_DecryptFinal_ex(ctx, output + len, &len);
    if (ret > 0) {
      result = SL_STATUS_OK;
    } else {
      memset(output, 0, length);
    }
  }

out:
  memset(unwrapped_key, 0, sizeof(unwrapped_key));
  EVP_CIPHER_CTX_free(ctx);
  return result;
}

sl_status_t se_aes_cbc(sl_se_command_context_t* cmd_ctx, const sl_se_key_descriptor_t* key,
                       sl_se_cipher_operation_t mode, size_t length, unsigned char iv[16],
                       const unsigned char* input, unsigned char* output) {
  (void)cmd_ctx;

  if (key == NULL || input == NULL || output == NULL || iv == NULL || length == 0) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  if (length & 0xf) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  size_t key_size = get_key_size(key);
  if (key_size == 0) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  uint8_t unwrapped_key[AES_256_LENGTH_BYTES];
  const uint8_t* key_ptr;
  if (!get_usable_key(key, unwrapped_key, key_size, &key_ptr)) {
    return SL_STATUS_FAIL;
  }

  EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();
  if (!ctx) {
    return SL_STATUS_FAIL;
  }

  sl_status_t result = SL_STATUS_FAIL;
  int len;
  const EVP_CIPHER* cipher =
    (key_size == AES_128_LENGTH_BYTES) ? EVP_aes_128_cbc() : EVP_aes_256_cbc();

  if (mode == SL_SE_ENCRYPT) {
    if (EVP_EncryptInit_ex(ctx, cipher, NULL, key_ptr, iv) != OPENSSL_OK) {
      goto out;
    }
    EVP_CIPHER_CTX_set_padding(ctx, 0);
    if (EVP_EncryptUpdate(ctx, output, &len, input, length) != OPENSSL_OK) {
      goto out;
    }
    if (EVP_EncryptFinal_ex(ctx, output + len, &len) != OPENSSL_OK) {
      goto out;
    }
    result = SL_STATUS_OK;
  } else {
    if (EVP_DecryptInit_ex(ctx, cipher, NULL, key_ptr, iv) != OPENSSL_OK) {
      goto out;
    }
    EVP_CIPHER_CTX_set_padding(ctx, 0);
    if (EVP_DecryptUpdate(ctx, output, &len, input, length) != OPENSSL_OK) {
      goto out;
    }
    if (EVP_DecryptFinal_ex(ctx, output + len, &len) != OPENSSL_OK) {
      goto out;
    }
    result = SL_STATUS_OK;
  }

out:
  memset(unwrapped_key, 0, sizeof(unwrapped_key));
  EVP_CIPHER_CTX_free(ctx);
  return result;
}

sl_status_t se_aes_ecb(sl_se_command_context_t* cmd_ctx, const sl_se_key_descriptor_t* key,
                       sl_se_cipher_operation_t mode, size_t length, const unsigned char* input,
                       unsigned char* output) {
  (void)cmd_ctx;

  if (key == NULL || input == NULL || output == NULL || (length & 0xFU) != 0U) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  size_t key_size = get_key_size(key);
  if (key_size == 0) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  uint8_t unwrapped_key[AES_256_LENGTH_BYTES];
  const uint8_t* key_ptr;
  if (!get_usable_key(key, unwrapped_key, key_size, &key_ptr)) {
    return SL_STATUS_FAIL;
  }

  EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();
  if (!ctx) {
    return SL_STATUS_FAIL;
  }

  sl_status_t result = SL_STATUS_FAIL;
  int len;
  const EVP_CIPHER* cipher =
    (key_size == AES_128_LENGTH_BYTES) ? EVP_aes_128_ecb() : EVP_aes_256_ecb();

  if (mode == SL_SE_ENCRYPT) {
    if (EVP_EncryptInit_ex(ctx, cipher, NULL, key_ptr, NULL) != OPENSSL_OK) {
      goto out;
    }
    EVP_CIPHER_CTX_set_padding(ctx, 0);
    if (EVP_EncryptUpdate(ctx, output, &len, input, length) != OPENSSL_OK) {
      goto out;
    }
    if (EVP_EncryptFinal_ex(ctx, output + len, &len) != OPENSSL_OK) {
      goto out;
    }
    result = SL_STATUS_OK;
  } else {
    if (EVP_DecryptInit_ex(ctx, cipher, NULL, key_ptr, NULL) != OPENSSL_OK) {
      goto out;
    }
    EVP_CIPHER_CTX_set_padding(ctx, 0);
    if (EVP_DecryptUpdate(ctx, output, &len, input, length) != OPENSSL_OK) {
      goto out;
    }
    if (EVP_DecryptFinal_ex(ctx, output + len, &len) != OPENSSL_OK) {
      goto out;
    }
    result = SL_STATUS_OK;
  }

out:
  memset(unwrapped_key, 0, sizeof(unwrapped_key));
  EVP_CIPHER_CTX_free(ctx);
  return result;
}

sl_status_t se_aes_cmac(sl_se_command_context_t* cmd_ctx, const sl_se_key_descriptor_t* key,
                        const unsigned char* input, size_t input_len, unsigned char* output) {
  (void)cmd_ctx;

  if (key == NULL || input == NULL || output == NULL) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  size_t key_size = get_key_size(key);
  if (key_size == 0) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  uint8_t unwrapped_key[AES_256_LENGTH_BYTES];
  const uint8_t* key_ptr;
  if (!get_usable_key(key, unwrapped_key, key_size, &key_ptr)) {
    return SL_STATUS_FAIL;
  }

  EVP_MAC* mac = EVP_MAC_fetch(NULL, "CMAC", NULL);
  if (!mac) {
    return SL_STATUS_FAIL;
  }

  EVP_MAC_CTX* ctx = EVP_MAC_CTX_new(mac);
  EVP_MAC_free(mac);
  if (!ctx) {
    return SL_STATUS_FAIL;
  }

  sl_status_t result = SL_STATUS_FAIL;
  const char* cipher_name = (key_size == AES_128_LENGTH_BYTES) ? "AES-128-CBC" : "AES-256-CBC";
  OSSL_PARAM params[2];
  params[0] = OSSL_PARAM_construct_utf8_string("cipher", (char*)cipher_name, 0);
  params[1] = OSSL_PARAM_construct_end();

  if (EVP_MAC_init(ctx, key_ptr, key_size, params) != OPENSSL_OK) {
    goto out;
  }
  if (EVP_MAC_update(ctx, input, input_len) != OPENSSL_OK) {
    goto out;
  }
  size_t out_len = 16;
  if (EVP_MAC_final(ctx, output, &out_len, 16) != OPENSSL_OK) {
    goto out;
  }
  result = SL_STATUS_OK;

out:
  memset(unwrapped_key, 0, sizeof(unwrapped_key));
  EVP_MAC_CTX_free(ctx);
  return result;
}

sl_status_t sl_se_generate_key(sl_se_command_context_t* cmd_ctx,
                               const sl_se_key_descriptor_t* key_out) {
  (void)cmd_ctx;

  if (key_out == NULL || key_out->storage.location.buffer.pointer == NULL) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  size_t key_size = get_key_size(key_out);
  if (key_size == 0) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  uint8_t* key_buf = key_out->storage.location.buffer.pointer;

  if (!crypto_random(key_buf, key_size)) {
    return SL_STATUS_FAIL;
  }

  if (key_out->storage.method == SL_SE_KEY_STORAGE_EXTERNAL_WRAPPED) {
    uint8_t* blob = malloc(key_size + SE_WRAPPED_KEY_OVERHEAD);
    if (!blob) {
      return SL_STATUS_FAIL;
    }

    uint8_t* iv = &blob[0];
    uint8_t* ciphertext = &blob[AES_GCM_IV_LENGTH];
    uint8_t* tag = &blob[AES_GCM_IV_LENGTH + key_size];

    if (!crypto_random(iv, AES_GCM_IV_LENGTH)) {
      free(blob);
      return SL_STATUS_FAIL;
    }

    EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();
    if (!ctx) {
      free(blob);
      return SL_STATUS_FAIL;
    }

    sl_status_t result = SL_STATUS_FAIL;
    int len;

    if (EVP_EncryptInit_ex(ctx, EVP_aes_256_gcm(), NULL, NULL, NULL) != OPENSSL_OK) {
      goto wrap_out;
    }
    if (EVP_EncryptInit_ex(ctx, NULL, NULL, fake_se_kek_buf, iv) != OPENSSL_OK) {
      goto wrap_out;
    }
    if (EVP_EncryptUpdate(ctx, ciphertext, &len, key_buf, key_size) != OPENSSL_OK) {
      goto wrap_out;
    }
    if (EVP_EncryptFinal_ex(ctx, ciphertext + len, &len) != OPENSSL_OK) {
      goto wrap_out;
    }
    if (EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_GET_TAG, AES_GCM_TAG_LENGTH, tag) != OPENSSL_OK) {
      goto wrap_out;
    }

    memcpy(key_buf, blob, key_size + SE_WRAPPED_KEY_OVERHEAD);
    result = SL_STATUS_OK;

  wrap_out:
    EVP_CIPHER_CTX_free(ctx);
    free(blob);
    return result;
  }

  return SL_STATUS_OK;
}

sl_se_key_descriptor_t se_key_descriptor_for_key_handle(key_handle_t* handle) {
  sl_se_key_descriptor_t descriptor = {0};

  descriptor.flags = handle->acl;

  switch (handle->alg) {
    case ALG_AES_128:
      descriptor.type = SL_SE_KEY_TYPE_AES_128;
      break;
    case ALG_AES_256:
      descriptor.type = SL_SE_KEY_TYPE_AES_256;
      break;
    case ALG_HMAC:
      descriptor.type = SL_SE_KEY_TYPE_SYMMETRIC;
      descriptor.size = handle->key.size;
      break;
    case ALG_ECC_P256:
      descriptor.type = SL_SE_KEY_TYPE_ECC_P256;
      descriptor.size = 32;
      break;
    case ALG_ECC_ED25519:
      descriptor.type = SL_SE_KEY_TYPE_ECC_ED25519;
      descriptor.size = 32;
      break;
    case ALG_ECC_X25519:
      descriptor.type = SL_SE_KEY_TYPE_ECC_X25519;
      descriptor.size = 32;
      break;
    default:
      descriptor.type = SL_SE_KEY_TYPE_SYMMETRIC;
      descriptor.size = handle->key.size;
      break;
  }

  switch (handle->storage_type) {
    case KEY_STORAGE_EXTERNAL_PLAINTEXT:
      descriptor.storage.method = SL_SE_KEY_STORAGE_EXTERNAL_PLAINTEXT;
      descriptor.storage.location.buffer.pointer = handle->key.bytes;
      descriptor.storage.location.buffer.size = handle->key.size;
      break;
    case KEY_STORAGE_EXTERNAL_WRAPPED:
      descriptor.storage.method = SL_SE_KEY_STORAGE_EXTERNAL_WRAPPED;
      descriptor.storage.location.buffer.pointer = handle->key.bytes;
      descriptor.storage.location.buffer.size = handle->key.size;
      break;
    case KEY_STORAGE_INTERNAL_IMMUTABLE:
      descriptor.storage.method = SL_SE_KEY_STORAGE_INTERNAL_IMMUTABLE;
      descriptor.storage.location.slot = handle->slot;
      break;
    case KEY_STORAGE_INTERNAL_VOLATILE:
      descriptor.storage.method = SL_SE_KEY_STORAGE_INTERNAL_VOLATILE;
      descriptor.storage.location.slot = handle->slot;
      break;
  }

  return descriptor;
}

// =============================================================================
// RNG
// =============================================================================

sl_status_t sl_se_get_random(sl_se_command_context_t* cmd_ctx, void* data, uint32_t num_bytes) {
  (void)cmd_ctx;
  if (data == NULL || num_bytes == 0) {
    return SL_STATUS_INVALID_PARAMETER;
  }
  return crypto_random(data, num_bytes) ? SL_STATUS_OK : SL_STATUS_FAIL;
}

// =============================================================================
// Hash functions
// =============================================================================

static const EVP_MD* get_evp_md(sl_se_hash_type_t hash_type) {
  switch (hash_type) {
    case SL_SE_HASH_SHA1:
      return EVP_sha1();
    case SL_SE_HASH_SHA224:
      return EVP_sha224();
    case SL_SE_HASH_SHA256:
      return EVP_sha256();
    case SL_SE_HASH_SHA384:
      return EVP_sha384();
    case SL_SE_HASH_SHA512:
      return EVP_sha512();
    default:
      return NULL;
  }
}

static size_t get_hash_size(sl_se_hash_type_t hash_type) {
  switch (hash_type) {
    case SL_SE_HASH_SHA1:
      return 20;
    case SL_SE_HASH_SHA224:
      return 28;
    case SL_SE_HASH_SHA256:
      return 32;
    case SL_SE_HASH_SHA384:
      return 48;
    case SL_SE_HASH_SHA512:
      return 64;
    default:
      return 0;
  }
}

sl_status_t se_hash(sl_se_command_context_t* cmd_ctx, sl_se_hash_type_t hash_type,
                    const uint8_t* message, unsigned int message_size, uint8_t* digest,
                    size_t digest_size) {
  (void)cmd_ctx;

  if (message == NULL || digest == NULL) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  const EVP_MD* md = get_evp_md(hash_type);
  if (md == NULL) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  size_t expected_size = get_hash_size(hash_type);
  if (digest_size < expected_size) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  unsigned int actual_size = 0;
  if (EVP_Digest(message, message_size, digest, &actual_size, md, NULL) != OPENSSL_OK) {
    return SL_STATUS_FAIL;
  }

  return SL_STATUS_OK;
}

sl_status_t se_hmac(sl_se_command_context_t* cmd_ctx, const sl_se_key_descriptor_t* key,
                    sl_se_hash_type_t hash_type, const uint8_t* message, size_t message_len,
                    uint8_t* output, size_t output_len) {
  (void)cmd_ctx;

  if (key == NULL || message == NULL || output == NULL) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  const EVP_MD* md = get_evp_md(hash_type);
  if (md == NULL) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  size_t key_size = get_key_size(key);
  if (key_size == 0) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  uint8_t unwrapped_key[AES_256_LENGTH_BYTES];
  const uint8_t* key_ptr;
  if (!get_usable_key(key, unwrapped_key, key_size, &key_ptr)) {
    return SL_STATUS_FAIL;
  }

  unsigned int actual_len = 0;
  unsigned char* result =
    HMAC(md, key_ptr, (int)key_size, message, message_len, output, &actual_len);

  memset(unwrapped_key, 0, sizeof(unwrapped_key));

  if (result == NULL || actual_len > output_len) {
    return SL_STATUS_FAIL;
  }

  return SL_STATUS_OK;
}

// Multipart hash context - uses OpenSSL's EVP_MD_CTX internally
// We store this in a static variable since the Gecko SDK context struct is too small
static EVP_MD_CTX* posix_multipart_ctx = NULL;

sl_status_t se_hash_sha256_multipart_starts(sl_se_sha256_multipart_context_t* sha256_ctx,
                                            sl_se_command_context_t* cmd_ctx) {
  (void)sha256_ctx;
  (void)cmd_ctx;

  if (posix_multipart_ctx != NULL) {
    EVP_MD_CTX_free(posix_multipart_ctx);
  }
  posix_multipart_ctx = EVP_MD_CTX_new();
  if (posix_multipart_ctx == NULL) {
    return SL_STATUS_FAIL;
  }

  if (EVP_DigestInit_ex(posix_multipart_ctx, EVP_sha256(), NULL) != OPENSSL_OK) {
    EVP_MD_CTX_free(posix_multipart_ctx);
    posix_multipart_ctx = NULL;
    return SL_STATUS_FAIL;
  }

  return SL_STATUS_OK;
}

sl_status_t se_hash_multipart_update(void* hash_type_ctx, sl_se_command_context_t* cmd_ctx,
                                     const uint8_t* input, size_t input_len) {
  (void)hash_type_ctx;
  (void)cmd_ctx;

  if (posix_multipart_ctx == NULL || input == NULL) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  if (EVP_DigestUpdate(posix_multipart_ctx, input, input_len) != OPENSSL_OK) {
    return SL_STATUS_FAIL;
  }

  return SL_STATUS_OK;
}

sl_status_t se_hash_multipart_finish(void* hash_type_ctx, sl_se_command_context_t* cmd_ctx,
                                     uint8_t* digest_out, size_t digest_len) {
  (void)hash_type_ctx;
  (void)cmd_ctx;

  if (posix_multipart_ctx == NULL || digest_out == NULL || digest_len < 32) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  unsigned int actual_len = 0;
  if (EVP_DigestFinal_ex(posix_multipart_ctx, digest_out, &actual_len) != OPENSSL_OK) {
    EVP_MD_CTX_free(posix_multipart_ctx);
    posix_multipart_ctx = NULL;
    return SL_STATUS_FAIL;
  }

  EVP_MD_CTX_free(posix_multipart_ctx);
  posix_multipart_ctx = NULL;

  return SL_STATUS_OK;
}

// =============================================================================
// Key derivation - HKDF
// =============================================================================

sl_status_t sl_se_derive_key_hkdf(sl_se_command_context_t* cmd_ctx,
                                  const sl_se_key_descriptor_t* in_key, sl_se_hash_type_t hash,
                                  const unsigned char* salt, size_t salt_len,
                                  const unsigned char* info, size_t info_len,
                                  sl_se_key_descriptor_t* out_key) {
  (void)cmd_ctx;

  if (in_key == NULL || out_key == NULL || out_key->storage.location.buffer.pointer == NULL) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  size_t ikm_size = get_key_size(in_key);
  if (ikm_size == 0) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  uint8_t unwrapped_ikm[AES_256_LENGTH_BYTES];
  const uint8_t* ikm_ptr;
  if (!get_usable_key(in_key, unwrapped_ikm, ikm_size, &ikm_ptr)) {
    return SL_STATUS_FAIL;
  }

  const EVP_MD* md = get_evp_md(hash);
  if (md == NULL) {
    memset(unwrapped_ikm, 0, sizeof(unwrapped_ikm));
    return SL_STATUS_INVALID_PARAMETER;
  }

  EVP_KDF* kdf = EVP_KDF_fetch(NULL, "HKDF", NULL);
  if (kdf == NULL) {
    memset(unwrapped_ikm, 0, sizeof(unwrapped_ikm));
    return SL_STATUS_FAIL;
  }

  EVP_KDF_CTX* kctx = EVP_KDF_CTX_new(kdf);
  EVP_KDF_free(kdf);
  if (kctx == NULL) {
    memset(unwrapped_ikm, 0, sizeof(unwrapped_ikm));
    return SL_STATUS_FAIL;
  }

  sl_status_t result = SL_STATUS_FAIL;
  const char* digest_name = EVP_MD_get0_name(md);

  OSSL_PARAM params[5];
  int idx = 0;
  params[idx++] = OSSL_PARAM_construct_utf8_string(OSSL_KDF_PARAM_DIGEST, (char*)digest_name, 0);
  params[idx++] = OSSL_PARAM_construct_octet_string(OSSL_KDF_PARAM_KEY, (void*)ikm_ptr, ikm_size);
  if (salt != NULL && salt_len > 0) {
    params[idx++] = OSSL_PARAM_construct_octet_string(OSSL_KDF_PARAM_SALT, (void*)salt, salt_len);
  }
  if (info != NULL && info_len > 0) {
    params[idx++] = OSSL_PARAM_construct_octet_string(OSSL_KDF_PARAM_INFO, (void*)info, info_len);
  }
  params[idx] = OSSL_PARAM_construct_end();

  size_t out_len = out_key->storage.location.buffer.size;
  if (out_key->size > 0 && out_key->size < out_len) {
    out_len = out_key->size;
  }

  if (EVP_KDF_derive(kctx, out_key->storage.location.buffer.pointer, out_len, params) ==
      OPENSSL_OK) {
    result = SL_STATUS_OK;
  }

  EVP_KDF_CTX_free(kctx);
  memset(unwrapped_ikm, 0, sizeof(unwrapped_ikm));
  return result;
}

// =============================================================================
// ECDH
// =============================================================================

sl_status_t sl_se_ecdh_compute_shared_secret(sl_se_command_context_t* cmd_ctx,
                                             const sl_se_key_descriptor_t* key_in_priv,
                                             const sl_se_key_descriptor_t* key_in_pub,
                                             const sl_se_key_descriptor_t* key_out) {
  (void)cmd_ctx;
  // ECDH is implemented at a higher level in crypto_ecc_compute_shared_secret
  // This stub exists for API compatibility
  (void)key_in_priv;
  (void)key_in_pub;
  (void)key_out;
  return SL_STATUS_FAIL;
}

// =============================================================================
// ECC Sign/Verify (P-256 ECDSA)
// =============================================================================

sl_status_t se_ecc_sign(sl_se_command_context_t* cmd_ctx, const sl_se_key_descriptor_t* key,
                        sl_se_hash_type_t hash_alg, bool hashed_message,
                        const unsigned char* message, size_t message_len, unsigned char* signature,
                        size_t signature_len) {
  (void)cmd_ctx;

  if (key == NULL || message == NULL || signature == NULL) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  // Only P-256 is currently supported
  if ((key->type & SL_SE_KEY_TYPE_ALGORITHM_MASK) != SL_SE_KEY_TYPE_ECC_WEIERSTRASS_PRIME_CUSTOM) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  // Get the private key
  const uint8_t* privkey = key->storage.location.buffer.pointer;
  if (privkey == NULL) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  // Hash the message if not already hashed
  uint8_t digest[64];
  const uint8_t* hash_ptr = message;
  size_t hash_len = message_len;

  if (!hashed_message) {
    const EVP_MD* md = get_evp_md(hash_alg);
    if (md == NULL) {
      return SL_STATUS_INVALID_PARAMETER;
    }
    unsigned int digest_len = 0;
    if (EVP_Digest(message, message_len, digest, &digest_len, md, NULL) != OPENSSL_OK) {
      return SL_STATUS_FAIL;
    }
    hash_ptr = digest;
    hash_len = digest_len;
  }

  // Create EC_KEY from raw private key bytes
  EC_KEY* ec_key = EC_KEY_new_by_curve_name(NID_X9_62_prime256v1);
  if (ec_key == NULL) {
    return SL_STATUS_FAIL;
  }

  BIGNUM* priv_bn = BN_bin2bn(privkey, 32, NULL);
  if (priv_bn == NULL) {
    EC_KEY_free(ec_key);
    return SL_STATUS_FAIL;
  }

  if (EC_KEY_set_private_key(ec_key, priv_bn) != OPENSSL_OK) {
    BN_free(priv_bn);
    EC_KEY_free(ec_key);
    return SL_STATUS_FAIL;
  }
  BN_free(priv_bn);

  // Compute public key from private key
  const EC_GROUP* group = EC_KEY_get0_group(ec_key);
  EC_POINT* pub_point = EC_POINT_new(group);
  if (pub_point == NULL) {
    EC_KEY_free(ec_key);
    return SL_STATUS_FAIL;
  }

  if (EC_POINT_mul(group, pub_point, EC_KEY_get0_private_key(ec_key), NULL, NULL, NULL) !=
      OPENSSL_OK) {
    EC_POINT_free(pub_point);
    EC_KEY_free(ec_key);
    return SL_STATUS_FAIL;
  }

  if (EC_KEY_set_public_key(ec_key, pub_point) != OPENSSL_OK) {
    EC_POINT_free(pub_point);
    EC_KEY_free(ec_key);
    return SL_STATUS_FAIL;
  }
  EC_POINT_free(pub_point);

  // Sign
  unsigned int sig_len = ECDSA_size(ec_key);
  uint8_t* der_sig = malloc(sig_len);
  if (der_sig == NULL) {
    EC_KEY_free(ec_key);
    return SL_STATUS_FAIL;
  }

  if (ECDSA_sign(0, hash_ptr, hash_len, der_sig, &sig_len, ec_key) != OPENSSL_OK) {
    free(der_sig);
    EC_KEY_free(ec_key);
    return SL_STATUS_FAIL;
  }
  EC_KEY_free(ec_key);

  // Convert DER signature to raw R||S format (64 bytes for P-256)
  const uint8_t* der_ptr = der_sig;
  ECDSA_SIG* ecdsa_sig = d2i_ECDSA_SIG(NULL, &der_ptr, sig_len);
  free(der_sig);

  if (ecdsa_sig == NULL) {
    return SL_STATUS_FAIL;
  }

  const BIGNUM* r;
  const BIGNUM* s;
  ECDSA_SIG_get0(ecdsa_sig, &r, &s);

  if (signature_len < 64) {
    ECDSA_SIG_free(ecdsa_sig);
    return SL_STATUS_INVALID_PARAMETER;
  }

  memset(signature, 0, 64);
  BN_bn2bin(r, signature + 32 - BN_num_bytes(r));
  BN_bn2bin(s, signature + 64 - BN_num_bytes(s));

  ECDSA_SIG_free(ecdsa_sig);
  return SL_STATUS_OK;
}

sl_status_t se_ecc_verify(sl_se_command_context_t* cmd_ctx, const sl_se_key_descriptor_t* key,
                          sl_se_hash_type_t hash_alg, bool hashed_message,
                          const unsigned char* message, size_t message_len,
                          const unsigned char* signature, size_t signature_len) {
  (void)cmd_ctx;

  if (key == NULL || message == NULL || signature == NULL) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  // Only P-256 is currently supported
  if ((key->type & SL_SE_KEY_TYPE_ALGORITHM_MASK) != SL_SE_KEY_TYPE_ECC_WEIERSTRASS_PRIME_CUSTOM) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  if (signature_len < 64) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  // Get the public key (64 bytes: X || Y)
  const uint8_t* pubkey = key->storage.location.buffer.pointer;
  if (pubkey == NULL) {
    return SL_STATUS_INVALID_PARAMETER;
  }

  // Hash the message if not already hashed
  uint8_t digest[64];
  const uint8_t* hash_ptr = message;
  size_t hash_len = message_len;

  if (!hashed_message) {
    const EVP_MD* md = get_evp_md(hash_alg);
    if (md == NULL) {
      return SL_STATUS_INVALID_PARAMETER;
    }
    unsigned int digest_len = 0;
    if (EVP_Digest(message, message_len, digest, &digest_len, md, NULL) != OPENSSL_OK) {
      return SL_STATUS_FAIL;
    }
    hash_ptr = digest;
    hash_len = digest_len;
  }

  // Create EC_KEY from raw public key bytes
  EC_KEY* ec_key = EC_KEY_new_by_curve_name(NID_X9_62_prime256v1);
  if (ec_key == NULL) {
    return SL_STATUS_FAIL;
  }

  // Create uncompressed point: 0x04 || X || Y
  uint8_t uncompressed[65];
  uncompressed[0] = 0x04;
  memcpy(uncompressed + 1, pubkey, 64);

  const EC_GROUP* group = EC_KEY_get0_group(ec_key);
  EC_POINT* pub_point = EC_POINT_new(group);
  if (pub_point == NULL) {
    EC_KEY_free(ec_key);
    return SL_STATUS_FAIL;
  }

  if (EC_POINT_oct2point(group, pub_point, uncompressed, sizeof(uncompressed), NULL) !=
      OPENSSL_OK) {
    EC_POINT_free(pub_point);
    EC_KEY_free(ec_key);
    return SL_STATUS_FAIL;
  }

  if (EC_KEY_set_public_key(ec_key, pub_point) != OPENSSL_OK) {
    EC_POINT_free(pub_point);
    EC_KEY_free(ec_key);
    return SL_STATUS_FAIL;
  }
  EC_POINT_free(pub_point);

  // Convert raw R||S to DER format
  ECDSA_SIG* ecdsa_sig = ECDSA_SIG_new();
  if (ecdsa_sig == NULL) {
    EC_KEY_free(ec_key);
    return SL_STATUS_FAIL;
  }

  BIGNUM* r = BN_bin2bn(signature, 32, NULL);
  BIGNUM* s = BN_bin2bn(signature + 32, 32, NULL);
  if (r == NULL || s == NULL) {
    BN_free(r);
    BN_free(s);
    ECDSA_SIG_free(ecdsa_sig);
    EC_KEY_free(ec_key);
    return SL_STATUS_FAIL;
  }

  if (ECDSA_SIG_set0(ecdsa_sig, r, s) != OPENSSL_OK) {
    BN_free(r);
    BN_free(s);
    ECDSA_SIG_free(ecdsa_sig);
    EC_KEY_free(ec_key);
    return SL_STATUS_FAIL;
  }
  // r and s are now owned by ecdsa_sig

  // Verify
  int verify_result = ECDSA_do_verify(hash_ptr, hash_len, ecdsa_sig, ec_key);

  ECDSA_SIG_free(ecdsa_sig);
  EC_KEY_free(ec_key);

  if (verify_result == 1) {
    return SL_STATUS_OK;
  } else {
    return SL_STATUS_INVALID_SIGNATURE;
  }
}

// =============================================================================
// Attestation stubs - these are implemented in task_stubs.c
// =============================================================================

// Note: se_sign_with_device_identity_key and se_sign_challenge are implemented
// in firmware/app/core-sim/src/posix/key_manager_task_port.c

// =============================================================================
// Tamper stub
// =============================================================================

sl_status_t se_configure_active_mode(secure_bool_t enter) {
  (void)enter;
  // No tamper support on POSIX
  return SL_STATUS_OK;
}
