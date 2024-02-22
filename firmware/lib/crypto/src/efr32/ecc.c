#include "ecc.h"

#include "assert.h"
#include "attributes.h"
#include "crypto_impl.h"
#include "secure_engine.h"

NO_OPTIMIZE secure_bool_t crypto_ecc_verify_hash(key_handle_t* key, uint8_t* hash,
                                                 uint32_t hash_size,
                                                 uint8_t signature[ECC_SIG_SIZE]) {
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

NO_OPTIMIZE secure_bool_t crypto_ecc_keypair_sign_hash(key_handle_t* keypair, uint8_t* hash,
                                                       uint32_t hash_size,
                                                       uint8_t signature[ECC_SIG_SIZE]) {
  if (!(keypair->acl & SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PRIVATE_KEY &&
        keypair->acl & SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PUBLIC_KEY)) {
    return SECURE_FALSE;
  }
  return crypto_ecc_sign_hash(keypair, keypair, hash, hash_size, signature);
}

NO_OPTIMIZE secure_bool_t crypto_ecc_sign_hash(key_handle_t* privkey, key_handle_t* pubkey,
                                               uint8_t* hash, uint32_t hash_size,
                                               uint8_t signature[ECC_SIG_SIZE]) {
  ASSERT(privkey && pubkey && hash && signature);

  if (!(privkey->acl & SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PRIVATE_KEY) &&
      !(pubkey->acl & SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PUBLIC_KEY)) {
    return SECURE_FALSE;
  }

  sl_se_command_context_t cmd_ctx = {0};
  sl_se_key_descriptor_t privkey_desc = se_key_descriptor_for_key_handle(privkey);
  sl_se_key_descriptor_t pubkey_desc = se_key_descriptor_for_key_handle(pubkey);
  volatile sl_status_t status = sl_se_init_command_context(&cmd_ctx);
  if (status != SL_STATUS_OK) {
    return SECURE_FALSE;
  }

  volatile secure_bool_t ok = SECURE_FALSE;

  SECURE_DO_ONCE({
    if (se_ecc_sign(&cmd_ctx, &privkey_desc, SL_SE_HASH_NONE, true, hash, hash_size, signature,
                    ECC_SIG_SIZE) == SL_STATUS_OK) {
      ok = SECURE_TRUE;
    }
  });

  // Verify after signing to prevent using a corrupted signature, which can leak the private key.
  SECURE_IF_FAILOUT(ok == SECURE_TRUE) {
    volatile secure_bool_t verify_ok = SECURE_FALSE;

    // Verify twice to mitigate FI.
    if (se_ecc_verify(&cmd_ctx, &pubkey_desc, SL_SE_HASH_NONE, true, hash, hash_size, signature,
                      ECC_SIG_SIZE) == SL_STATUS_OK) {
      verify_ok = SECURE_TRUE;
    }

    SECURE_IF_FAILIN(verify_ok != SECURE_TRUE) { return SECURE_FALSE; }

    // Ideally `ok` isn't a bool, but we can't control the return value of the SE being SL_STATUS_OK
    SECURE_DO({
      verify_ok = SECURE_FALSE;  // Reset to closed state
    });

    if (se_ecc_verify(&cmd_ctx, &pubkey_desc, SL_SE_HASH_NONE, true, hash, hash_size, signature,
                      ECC_SIG_SIZE) == SL_STATUS_OK) {
      verify_ok = SECURE_TRUE;
    }

    SECURE_IF_FAILOUT(verify_ok == SECURE_TRUE) { return SECURE_TRUE; }

    return SECURE_FALSE;
  }

  return SECURE_FALSE;
}
