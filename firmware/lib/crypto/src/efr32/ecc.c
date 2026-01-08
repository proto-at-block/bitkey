#include "ecc.h"

#include "assert.h"
#include "attributes.h"
#include "crypto_impl.h"
#include "secure_engine.h"
#include "secutils.h"
#include "sl_se_manager_types.h"

#include <string.h>

NO_OPTIMIZE secure_bool_t crypto_ecc_verify_hash(key_handle_t* key, const uint8_t* hash,
                                                 uint32_t hash_size,
                                                 const uint8_t signature[ECC_SIG_SIZE]) {
  ASSERT(key && hash && signature);

  sl_se_command_context_t cmd_ctx = {0};
  sl_se_key_descriptor_t key_desc = se_key_descriptor_for_key_handle(key);
  volatile sl_status_t status = sl_se_init_command_context(&cmd_ctx);
  if (status != SL_STATUS_OK) {
    return SECURE_FALSE;
  }

  volatile secure_bool_t ok = SECURE_FALSE;

  SECURE_DO_ONCE({
    if (se_ecc_verify(&cmd_ctx, &key_desc, SL_SE_HASH_NONE, true, hash, hash_size, signature,
                      ECC_SIG_SIZE) == SL_STATUS_OK) {
      ok = SECURE_TRUE;
    }
  });

  // NOTE: This check may get optimized away. The caller should call this function multiple times
  // for better protection against FI.
  SECURE_IF_FAILOUT(ok == SECURE_TRUE) { return SECURE_TRUE; }

  return SECURE_FALSE;
}

// The bootloader doesn't need to sign or exchange keys.
#ifdef IMAGE_TYPE_APPLICATION

bool crypto_ecc_compute_shared_secret(key_handle_t* private_key, key_handle_t* public_key,
                                      key_handle_t* secret) {
  sl_se_key_descriptor_t private_key_desc = se_key_descriptor_for_key_handle(private_key);
  sl_se_key_descriptor_t public_key_desc = se_key_descriptor_for_key_handle(public_key);
  sl_se_key_descriptor_t secret_desc = se_key_descriptor_for_key_handle(secret);
  sl_se_command_context_t cmd_ctx = {0};

  if (sl_se_init_command_context(&cmd_ctx) != SL_STATUS_OK) {
    return false;
  }

  if (sl_se_ecdh_compute_shared_secret(&cmd_ctx, &private_key_desc, &public_key_desc,
                                       &secret_desc) != SL_STATUS_OK) {
    return false;
  }
  return true;
}

NO_OPTIMIZE secure_bool_t crypto_ecc_sign_hash(key_handle_t* privkey, uint8_t* hash,
                                               uint32_t hash_size,
                                               uint8_t signature[ECC_SIG_SIZE]) {
  ASSERT(privkey && hash && signature);

  if (!(privkey->acl & SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PRIVATE_KEY)) {
    return SECURE_FALSE;
  }

  sl_se_command_context_t cmd_ctx = {0};
  sl_se_key_descriptor_t privkey_desc = se_key_descriptor_for_key_handle(privkey);
  volatile sl_status_t status = sl_se_init_command_context(&cmd_ctx);
  if (status != SL_STATUS_OK) {
    return SECURE_FALSE;
  }

  volatile secure_bool_t ok = SECURE_FALSE;

  SECURE_DO_ONCE({
    if (se_ecc_sign(&cmd_ctx, &privkey_desc, SL_SE_HASH_NONE, true, hash, hash_size, signature,
                    ECC_SIG_SIZE) == SL_STATUS_OK) {
      if (crypto_ecc_secp256k1_normalize_signature(signature)) {
        ok = SECURE_TRUE;
      }
    }
  });

  SECURE_IF_FAILOUT(ok == SECURE_TRUE) { return SECURE_TRUE; }

  return SECURE_FALSE;
}

#endif
