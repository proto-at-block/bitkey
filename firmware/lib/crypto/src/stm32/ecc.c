#include "ecc.h"

#include "curve25519.h"
#include "mcu_pka.h"
#include "secure_rng.h"
#include "secutils.h"

#include <stdint.h>
#include <string.h>

NO_OPTIMIZE secure_bool_t crypto_ecc_verify_hash(key_handle_t* key, const uint8_t* hash,
                                                 uint32_t hash_size,
                                                 const uint8_t signature[ECC_SIG_SIZE]) {
  ASSERT(key != NULL);
  ASSERT(hash != NULL);
  ASSERT(signature != NULL);

  // Only P-256 is currently supported
  if (key->alg != ALG_ECC_P256) {
    return SECURE_FALSE;
  }

  const uint8_t* pubkey_x;
  const uint8_t* pubkey_y;

  if (key->key.size != ECC_PUBKEY_SIZE_ECDSA_UNCOMPRESSED) {
    return SECURE_FALSE;
  }

  pubkey_x = &key->key.bytes[0];
  pubkey_y = &key->key.bytes[MCU_PKA_SECP256R1_SIZE];

  // Get P-256 curve parameters
  mcu_pka_curve_params_t curve_params;
  if (mcu_pka_get_curve_params(MCU_PKA_CURVE_SECP256R1, &curve_params) != MCU_ERROR_OK) {
    return SECURE_FALSE;
  }

  mcu_pka_public_key_t public_key = {.x = pubkey_x, .y = pubkey_y, .size = MCU_PKA_SECP256R1_SIZE};

  // Signature format: r || s (32 bytes each)
  const uint8_t* sig_r = &signature[0];
  const uint8_t* sig_s = &signature[MCU_PKA_SECP256R1_SIZE];

  mcu_pka_signature_t sig = {.r = sig_r, .s = sig_s, .size = MCU_PKA_SECP256R1_SIZE};

  // Verify the signature using hardware PKA
  mcu_err_t result = mcu_pka_ecdsa_verify(&curve_params, &public_key, hash, hash_size, &sig);

  return (result == MCU_ERROR_OK) ? SECURE_TRUE : SECURE_FALSE;
}

// The bootloader doesn't need to sign or exchange keys.
#ifdef IMAGE_TYPE_APPLICATION

bool crypto_ecc_compute_shared_secret(key_handle_t* keypair, key_handle_t* public_key,
                                      key_handle_t* secret) {
  // Keypair is actually just the private key on stm32
  ASSERT(keypair != NULL && keypair->alg == ALG_ECC_X25519 &&
         keypair->storage_type == KEY_STORAGE_EXTERNAL_PLAINTEXT &&
         keypair->key.size == CURVE25519_KEY_LEN);
  ASSERT(public_key != NULL && public_key->alg == ALG_ECC_X25519 &&
         public_key->storage_type == KEY_STORAGE_EXTERNAL_PLAINTEXT &&
         public_key->key.size == CURVE25519_KEY_LEN);
  ASSERT(secret != NULL && secret->storage_type == KEY_STORAGE_EXTERNAL_PLAINTEXT &&
         secret->key.size == EC_SECRET_SIZE_X25519);

  curve25519_get_shared_secret(secret->key.bytes, keypair->key.bytes, public_key->key.bytes);
  return true;
}

bool crypto_ecc_generate_random_scalar(uint8_t* scalar_bytes, size_t scalar_size) {
  // Generate random scalar in valid range [1, n-1] using rejection sampling
  // FIPS 186-5 A2.2: ECDSA Key Pair Generation by Rejection Sampling
  ASSERT(scalar_bytes != NULL);
  ASSERT(scalar_size == MCU_PKA_SECP256R1_SIZE);

  // Get P-256 curve parameters
  mcu_pka_curve_params_t curve_params;
  if (mcu_pka_get_curve_params(MCU_PKA_CURVE_SECP256R1, &curve_params) != MCU_ERROR_OK) {
    return false;
  }

  // For P-256, probability of rejection is extremely low.
  const uint8_t max_attempts = SECP256R1_KEYGEN_MAX_ATTEMPTS;
  for (uint8_t attempt = 0; attempt < max_attempts; attempt++) {
    if (!crypto_random(scalar_bytes, scalar_size)) {
      return false;
    }

    // Validate that the key is in the valid range [1, n-1]
    if (mcu_pka_validate_scalar(&curve_params, scalar_bytes, scalar_size) == MCU_ERROR_OK) {
      return true;
    }
  }

  return false;
}

bool crypto_ecc_derive_public_key(key_handle_t* privkey, key_handle_t* public_key) {
  ASSERT(privkey != NULL);
  ASSERT(privkey->alg == ALG_ECC_P256);
  ASSERT(privkey->storage_type == KEY_STORAGE_EXTERNAL_PLAINTEXT);
  ASSERT(privkey->key.size == ECC_PRIVKEY_SIZE);
  ASSERT(public_key != NULL);
  ASSERT(public_key->alg == ALG_ECC_P256);
  ASSERT(public_key->storage_type == KEY_STORAGE_EXTERNAL_PLAINTEXT);
  ASSERT(public_key->key.size == ECC_PUBKEY_SIZE_ECDSA_UNCOMPRESSED);

  // Get P-256 curve parameters
  mcu_pka_curve_params_t curve_params;
  if (mcu_pka_get_curve_params(MCU_PKA_CURVE_SECP256R1, &curve_params) != MCU_ERROR_OK) {
    return false;
  }

  // Compute public key: Q = d * G (scalar multiplication of base point by private key)
  // Public key format: x || y (32 bytes each, uncompressed)
  uint8_t* pubkey_x = &public_key->key.bytes[0];
  uint8_t* pubkey_y = &public_key->key.bytes[MCU_PKA_SECP256R1_SIZE];

  mcu_err_t result =
    mcu_pka_ecc_mul(&curve_params, privkey->key.bytes, privkey->key.size, curve_params.base_point_x,
                    curve_params.base_point_y, pubkey_x, pubkey_y);

  return (result == MCU_ERROR_OK);
}

NO_OPTIMIZE secure_bool_t crypto_ecc_sign_hash(key_handle_t* privkey, uint8_t* hash,
                                               uint32_t hash_size,
                                               uint8_t signature[ECC_SIG_SIZE]) {
  ASSERT(privkey != NULL);
  ASSERT(hash != NULL);
  ASSERT(signature != NULL);

  // Only P-256 is currently supported
  if (privkey->alg != ALG_ECC_P256) {
    return SECURE_FALSE;
  }

  // Verify the private key size
  if (privkey->key.size != ECC_PRIVKEY_SIZE) {
    return SECURE_FALSE;
  }

  // Get P-256 curve parameters
  mcu_pka_curve_params_t curve_params;
  if (mcu_pka_get_curve_params(MCU_PKA_CURVE_SECP256R1, &curve_params) != MCU_ERROR_OK) {
    return SECURE_FALSE;
  }

  // Generate random k value (ephemeral secret)
  // NOTE: k must be unique for each signature to prevent private key exposure
  uint8_t k[MCU_PKA_SECP256R1_SIZE];
  if (!crypto_ecc_generate_random_scalar(k, sizeof(k))) {
    memzero(k, sizeof(k));
    return SECURE_FALSE;
  }

  // Allocate buffers for signature components
  uint8_t sig_r[MCU_PKA_SECP256R1_SIZE];
  uint8_t sig_s[MCU_PKA_SECP256R1_SIZE];

  // Sign using hardware PKA
  mcu_err_t result =
    mcu_pka_ecdsa_sign(&curve_params, privkey->key.bytes, hash, hash_size, k, sig_r, sig_s);

  if (result != MCU_ERROR_OK) {
    memzero(k, sizeof(k));
    memzero(sig_r, sizeof(sig_r));
    memzero(sig_s, sizeof(sig_s));
    return SECURE_FALSE;
  }

  // Format signature as r || s (32 bytes each)
  memcpy(&signature[0], sig_r, MCU_PKA_SECP256R1_SIZE);
  memcpy(&signature[MCU_PKA_SECP256R1_SIZE], sig_s, MCU_PKA_SECP256R1_SIZE);

  // Zeroize secrets
  memzero(k, sizeof(k));
  memzero(sig_r, sizeof(sig_r));
  memzero(sig_s, sizeof(sig_s));

  return SECURE_TRUE;
}

#endif
