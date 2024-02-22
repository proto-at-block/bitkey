#include "key_management.h"

#include "assert.h"
#include "crypto_impl.h"
#include "log.h"

typedef enum {
  OP_IMPORT = 0,
  OP_EXPORT = 1,
} key_management_op_t;

sl_se_key_descriptor_t se_key_descriptor_for_key_handle(key_handle_t* handle) {
  sl_se_key_descriptor_t descriptor = {0};

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
      descriptor.size = handle->key.size;
      break;
    case ALG_ECC_ED25519:
      descriptor.type = SL_SE_KEY_TYPE_ECC_ED25519;
      descriptor.size = handle->key.size;
      break;
    case ALG_KEY_DERIVATION:
      descriptor.type = SL_SE_KEY_TYPE_SYMMETRIC;
      descriptor.size = handle->key.size;
      break;
    default:
      ASSERT(false);
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

  descriptor.flags = handle->acl;

  return descriptor;
}

bool generate_key(key_handle_t* key) {
  if (!key)
    return false;

  sl_se_command_context_t cmd_ctx = {0};
  sl_se_key_descriptor_t key_desc = se_key_descriptor_for_key_handle(key);
  sl_status_t status = sl_se_init_command_context(&cmd_ctx);
  if (status != SL_STATUS_OK) {
    return false;
  }

  status = sl_se_generate_key(&cmd_ctx, &key_desc);
  if (status != SL_STATUS_OK) {
    return false;
  }

  return true;
}

static bool manage_key_op(key_management_op_t op, key_handle_t* key_in, key_handle_t* key_out) {
  if (!key_in || !key_out)
    return false;

  sl_se_command_context_t cmd_ctx = {0};
  sl_status_t status = sl_se_init_command_context(&cmd_ctx);
  if (status != SL_STATUS_OK) {
    return false;
  }

  sl_se_key_descriptor_t in = se_key_descriptor_for_key_handle(key_in);
  sl_se_key_descriptor_t out = se_key_descriptor_for_key_handle(key_out);

  if (op == OP_IMPORT) {
    status = sl_se_import_key(&cmd_ctx, &in, &out);
  } else if (op == OP_EXPORT) {
    status = sl_se_export_key(&cmd_ctx, &in, &out);
  }

  if (status != SL_STATUS_OK) {
    LOGE("Key management operation failed: %ld", status);
    return false;
  }

  return true;
}

bool import_key(key_handle_t* key_in, key_handle_t* key_out) {
  return manage_key_op(OP_IMPORT, key_in, key_out);
}

bool export_key(key_handle_t* key_in, key_handle_t* key_out) {
  return manage_key_op(OP_EXPORT, key_in, key_out);
}

bool export_pubkey(key_handle_t* key_in, key_handle_t* key_out) {
  if (!key_in || !key_out) {
    return false;
  }

  sl_se_command_context_t cmd_ctx = {0};
  sl_status_t status = sl_se_init_command_context(&cmd_ctx);
  if (status != SL_STATUS_OK) {
    return false;
  }

  sl_se_key_descriptor_t in = se_key_descriptor_for_key_handle(key_in);
  sl_se_key_descriptor_t out = se_key_descriptor_for_key_handle(key_out);

  if (sl_se_export_public_key(&cmd_ctx, &in, &out) != SL_STATUS_OK) {
    return false;
  }

  // p256 pubkeys have different formats, but ed25519 pubkeys are always 32 bytes
  if (key_out->alg == ALG_ECC_ED25519) {
    key_out->key.size = 32;
  }

  return true;
}
