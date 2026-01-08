#include "aes.h"

#include "crypto_impl.h"
#include "key_management.h"
#include "secure_engine.h"
#include "secure_rng.h"

typedef enum {
  OP_ENCRYPT = 0,
  OP_DECRYPT = 1,
} operation_t;

static bool aes_gcm_op(operation_t op, const uint8_t* input, uint8_t* output, uint32_t length,
                       uint8_t const iv[AES_GCM_IV_LENGTH], uint8_t tag[AES_GCM_TAG_LENGTH],
                       uint8_t const* aad, uint32_t aad_length, key_handle_t* key) {
  if (!input || !output || !key) {
    return false;
  }

  sl_se_command_context_t cmd_ctx = {0};
  sl_se_key_descriptor_t key_desc = se_key_descriptor_for_key_handle(key);
  sl_status_t status = sl_se_init_command_context(&cmd_ctx);
  if (status != SL_STATUS_OK) {
    return false;
  }

  sl_se_cipher_operation_t se_cipher_operation = (op == OP_ENCRYPT) ? SL_SE_ENCRYPT : SL_SE_DECRYPT;

  status = se_aes_gcm(&cmd_ctx, &key_desc, se_cipher_operation, length, iv, aad, aad_length, input,
                      output, tag);
  return status == SL_STATUS_OK;
}

bool aes_gcm_encrypt(const uint8_t* plaintext, uint8_t* ciphertext, uint32_t length,
                     uint8_t const iv[AES_GCM_IV_LENGTH], uint8_t tag[AES_GCM_TAG_LENGTH],
                     uint8_t const* aad, uint32_t aad_length, key_handle_t* key) {
  return aes_gcm_op(OP_ENCRYPT, plaintext, ciphertext, length, iv, tag, aad, aad_length, key);
}

bool aes_gcm_decrypt(const uint8_t* ciphertext, uint8_t* plaintext, uint32_t length,
                     uint8_t const iv[AES_GCM_IV_LENGTH], uint8_t tag[AES_GCM_TAG_LENGTH],
                     uint8_t const* aad, uint32_t aad_length, key_handle_t* key) {
  return aes_gcm_op(OP_DECRYPT, ciphertext, plaintext, length, iv, tag, aad, aad_length, key);
}

static bool aes_cbc_op(operation_t op, uint8_t* input, uint8_t* output, uint32_t length,
                       uint8_t iv[AES_CBC_IV_LENGTH], key_handle_t* key) {
  if (!input || !output || !key) {
    return false;
  }
  sl_se_command_context_t cmd_ctx = {0};
  sl_se_key_descriptor_t key_desc = se_key_descriptor_for_key_handle(key);
  sl_status_t status = sl_se_init_command_context(&cmd_ctx);
  if (status != SL_STATUS_OK) {
    return false;
  }

  sl_se_cipher_operation_t se_cipher_operation = (op == OP_ENCRYPT) ? SL_SE_ENCRYPT : SL_SE_DECRYPT;

  status = se_aes_cbc(&cmd_ctx, &key_desc, se_cipher_operation, length, iv, input, output);
  if (status != SL_STATUS_OK) {
    return false;
  }

  return true;
}

bool aes_cbc_encrypt(uint8_t* plaintext, uint8_t* ciphertext, uint32_t length,
                     uint8_t iv[AES_CBC_IV_LENGTH], key_handle_t* key) {
  return aes_cbc_op(OP_ENCRYPT, plaintext, ciphertext, length, iv, key);
}

bool aes_cbc_decrypt(uint8_t* ciphertext, uint8_t* plaintext, uint32_t length,
                     uint8_t iv[AES_CBC_IV_LENGTH], key_handle_t* key) {
  return aes_cbc_op(OP_DECRYPT, ciphertext, plaintext, length, iv, key);
}

bool aes_cmac(uint8_t* data, uint32_t data_length, uint8_t mac[AES_CMAC_LENGTH],
              key_handle_t* key) {
  if (!data || !mac || !key) {
    return false;
  }

  sl_se_command_context_t cmd_ctx = {0};
  sl_se_key_descriptor_t key_desc = se_key_descriptor_for_key_handle(key);
  sl_status_t status = sl_se_init_command_context(&cmd_ctx);
  if (status != SL_STATUS_OK) {
    return false;
  }

  status = se_aes_cmac(&cmd_ctx, &key_desc, data, data_length, mac);
  if (status != SL_STATUS_OK) {
    return false;
  }

  return true;
}

bool aes_one_block_op(operation_t op, uint8_t* input_block, uint8_t* output_block,
                      uint32_t block_length, key_handle_t* key) {
  if (!input_block || !output_block || !key || (block_length != AES_128_LENGTH_BYTES)) {
    return false;
  }

  sl_se_command_context_t cmd_ctx = {0};
  sl_se_key_descriptor_t key_desc = se_key_descriptor_for_key_handle(key);
  sl_status_t status = sl_se_init_command_context(&cmd_ctx);
  if (status != SL_STATUS_OK) {
    return false;
  }

  sl_se_cipher_operation_t se_cipher_operation = (op == OP_ENCRYPT) ? SL_SE_ENCRYPT : SL_SE_DECRYPT;
  status =
    se_aes_ecb(&cmd_ctx, &key_desc, se_cipher_operation, block_length, input_block, output_block);
  if (status != SL_STATUS_OK) {
    return false;
  }

  return true;
}

bool aes_one_block_encrypt(uint8_t* pt_block, uint8_t* ct_block, uint32_t block_length,
                           key_handle_t* key) {
  return aes_one_block_op(OP_ENCRYPT, pt_block, ct_block, block_length, key);
}

bool aes_one_block_decrypt(uint8_t* ct_block, uint8_t* pt_block, uint32_t block_length,
                           key_handle_t* key) {
  return aes_one_block_op(OP_DECRYPT, ct_block, pt_block, block_length, key);
}
