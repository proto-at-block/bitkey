#include "hkdf.h"

#include "assert.h"
#include "crypto_impl.h"
#include "secure_engine.h"

bool crypto_hkdf(key_handle_t* key_in, hash_alg_t hash, uint8_t const* salt, size_t salt_len,
                 uint8_t const* info, size_t info_len, key_handle_t* key_out) {
  ASSERT(key_in && key_out);

  sl_se_command_context_t cmd_ctx = {0};
  if (sl_se_init_command_context(&cmd_ctx) != SL_STATUS_OK) {
    return false;
  }

  sl_se_key_descriptor_t key_desc_in = se_key_descriptor_for_key_handle(key_in);
  sl_se_key_descriptor_t key_desc_out = se_key_descriptor_for_key_handle(key_out);

  key_desc_out.type = SL_SE_KEY_TYPE_SYMMETRIC;  // For key derivation

  return (sl_se_derive_key_hkdf(&cmd_ctx, &key_desc_in, convert_hash_type(hash), salt, salt_len,
                                info, info_len, &key_desc_out) == SL_STATUS_OK);
}
