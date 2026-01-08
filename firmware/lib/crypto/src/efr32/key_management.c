#include "key_management.h"

#include "assert.h"
#include "crypto_impl.h"
#include "log.h"

typedef enum {
  OP_IMPORT = 0,
  OP_EXPORT = 1,
} key_management_op_t;

// secp256k1
static const uint8_t p[] = {
  0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
  0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xfe, 0xff, 0xff, 0xfc, 0x2f,
};
static const uint8_t N[] = {
  0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xfe,
  0xba, 0xae, 0xdc, 0xe6, 0xaf, 0x48, 0xa0, 0x3b, 0xbf, 0xd2, 0x5e, 0x8c, 0xd0, 0x36, 0x41, 0x41,
};
static const uint8_t Gx[] = {
  0x79, 0xbe, 0x66, 0x7e, 0xf9, 0xdc, 0xbb, 0xac, 0x55, 0xa0, 0x62, 0x95, 0xce, 0x87, 0x0b, 0x07,
  0x02, 0x9b, 0xfc, 0xdb, 0x2d, 0xce, 0x28, 0xd9, 0x59, 0xf2, 0x81, 0x5b, 0x16, 0xf8, 0x17, 0x98,
};
static const uint8_t Gy[] = {
  0x48, 0x3a, 0xda, 0x77, 0x26, 0xa3, 0xc4, 0x65, 0x5d, 0xa4, 0xfb, 0xfc, 0x0e, 0x11, 0x08, 0xa8,
  0xfd, 0x17, 0xb4, 0x48, 0xa6, 0x85, 0x54, 0x19, 0x9c, 0x47, 0xd0, 0x8f, 0xfb, 0x10, 0xd4, 0xb8,
};
static const uint8_t a[] = {
  0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
  0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
};
static const uint8_t b[] = {
  0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
  0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x07,
};

const sl_se_custom_weierstrass_prime_domain_t secp256k1_domain = {
  .size = SECP256K1_DOMAIN_SIZE,
  .p = p,
  .N = N,
  .Gx = Gx,
  .Gy = Gy,
  .a = a,
  .b = b,
  .a_is_zero = true,
  .a_is_minus_three = false,
};

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
      descriptor.size = handle->key.size;
      break;
    case ALG_ECC_ED25519:
      descriptor.type = SL_SE_KEY_TYPE_ECC_ED25519;
      descriptor.size = handle->key.size;
      break;
    case ALG_ECC_X25519:
      descriptor.type = SL_SE_KEY_TYPE_ECC_X25519;
      descriptor.size = handle->key.size;
      break;
    case ALG_ECC_SECP256K1:
      descriptor.type = SL_SE_KEY_TYPE_ECC_WEIERSTRASS_PRIME_CUSTOM;
      descriptor.size = handle->key.size;
      descriptor.domain = &secp256k1_domain;
      descriptor.flags |= SL_SE_KEY_FLAG_ASYMMETRIC_USES_CUSTOM_DOMAIN;
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

uint32_t key_management_custom_domain_prepare(key_algorithm_t alg, uint8_t* buffer, uint32_t size) {
  ASSERT(buffer);
  ASSERT(alg == ALG_ECC_SECP256K1);

  const uint32_t needed_size = SECP256K1_CUSTOM_DOMAIN_OVERHEAD;
  ASSERT(size >= needed_size);

  uint32_t off = 0;
  memcpy(buffer, secp256k1_domain.p, SECP256K1_DOMAIN_SIZE);
  off += SECP256K1_DOMAIN_SIZE;

  memcpy(buffer + off, secp256k1_domain.N, SECP256K1_DOMAIN_SIZE);
  off += SECP256K1_DOMAIN_SIZE;

  memcpy(buffer + off, secp256k1_domain.Gx, SECP256K1_DOMAIN_SIZE);
  off += SECP256K1_DOMAIN_SIZE;

  memcpy(buffer + off, secp256k1_domain.Gy, SECP256K1_DOMAIN_SIZE);
  off += SECP256K1_DOMAIN_SIZE;

  memcpy(buffer + off, secp256k1_domain.a, SECP256K1_DOMAIN_SIZE);
  off += SECP256K1_DOMAIN_SIZE;

  memcpy(buffer + off, secp256k1_domain.b, SECP256K1_DOMAIN_SIZE);
  off += SECP256K1_DOMAIN_SIZE;

  ASSERT(off == needed_size);

  return off;
}

bool crypto_sign_with_device_identity(uint8_t* data, uint32_t data_size, uint8_t* signature,
                                      uint32_t signature_size) {
  return (se_sign_with_device_identity_key(data, data_size, signature, signature_size) ==
          SL_STATUS_OK);
}
