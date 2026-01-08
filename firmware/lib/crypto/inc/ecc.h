#pragma once

#include "hash.h"
#include "key_management.h"
#include "secutils.h"

#include <stdbool.h>

#define ECC_SIG_SIZE (64u)

// Generic ECC

#define ECC_PUBKEY_SIZE_ED25519            (32u)
#define ECC_PUBKEY_SIZE_ECDSA_UNCOMPRESSED (64u)
#define ECC_PRIVKEY_SIZE                   (32u)

#define EC_PUBKEY_SIZE_X25519  (32)
#define EC_PRIVKEY_SIZE_X25519 (32)
#define EC_SECRET_SIZE_X25519  (32)

secure_bool_t crypto_ecc_verify_hash(key_handle_t* key, const uint8_t* hash, uint32_t hash_size,
                                     const uint8_t signature[ECC_SIG_SIZE]);

// Does NOT verify the signature after signing. If that is important for the security of
// the caller, the caller must verify the signature.
secure_bool_t crypto_ecc_sign_hash(key_handle_t* privkey, uint8_t* hash, uint32_t hash_size,
                                   uint8_t signature[ECC_SIG_SIZE]);

bool crypto_ecc_validate_private_key(key_handle_t* privkey);

bool crypto_ecc_compute_shared_secret(key_handle_t* private_key, key_handle_t* public_key,
                                      key_handle_t* secret);

// secp256k1

#define SECP256K1_KEY_SIZE            (32u)
#define SECP256K1_KEYPAIR_SIZE        (96u)
#define SECP256K1_SEC1_KEY_SIZE       (33u)
#define SECP256R1_KEYGEN_MAX_ATTEMPTS (3u)

void crypto_ecc_secp256k1_init(void);

// `key`'s members may be uninitialized, except the internal key buffer and
// size must be correctly set and be large enough to store an ECC key (implementation-dependent)
bool crypto_ecc_secp256k1_generate_keypair(key_handle_t* key);

bool crypto_ecc_secp256k1_load_keypair(uint8_t privkey[SECP256K1_KEY_SIZE], key_handle_t* key);

bool crypto_ecc_secp256k1_schnorr_sign_hash32(key_handle_t* key, uint8_t* hash, uint8_t* signature,
                                              uint32_t signature_size);

bool crypto_ecc_secp256k1_schnorr_verify_hash32(key_handle_t* key, uint8_t* hash,
                                                uint8_t* signature, uint32_t signature_size,
                                                bool* verify_result);

bool crypto_ecc_secp256k1_schnorr_sign(key_handle_t* key, uint8_t* message, uint32_t message_size,
                                       uint8_t* signature, uint32_t signature_size);

bool crypto_ecc_secp256k1_schnorr_verify(key_handle_t* key, uint8_t* message, uint32_t message_size,
                                         uint8_t* signature, uint32_t signature_size,
                                         bool* verify_result);

bool crypto_ecc_secp256k1_ecdsa_sign_hash32(key_handle_t* privkey, uint8_t* hash,
                                            uint8_t* signature, uint32_t signature_size);

bool crypto_ecc_secp256k1_priv_verify(const uint8_t privkey[SECP256K1_KEY_SIZE]);

bool crypto_ecc_secp256k1_verify_signature(const uint8_t pubkey[SECP256K1_KEY_SIZE],
                                           const uint8_t* message, uint32_t message_size,
                                           const uint8_t signature[ECC_SIG_SIZE]);

// Returns the x-only public key
bool crypto_ecc_secp256k1_priv_to_xonly_pub(uint8_t privkey[SECP256K1_KEY_SIZE],
                                            uint8_t pubkey_out[SECP256K1_KEY_SIZE],
                                            bool* y_is_even);

bool crypto_ecc_secp256k1_priv_to_sec_encoded_pub(
  uint8_t privkey[SECP256K1_KEY_SIZE], uint8_t sec_encoded_pubkey_out[SECP256K1_SEC1_KEY_SIZE]);

bool crypto_ecc_secp256k1_priv_tweak_add(uint8_t privkey[SECP256K1_KEY_SIZE], uint8_t* tweak);

bool crypto_ecc_secp256k1_pub_tweak_add(uint8_t sec_encoded_pubkey[SECP256K1_SEC1_KEY_SIZE],
                                        uint8_t tweak[SECP256K1_KEY_SIZE]);

bool crypto_ecc_secp256k1_normalize_signature(uint8_t signature[ECC_SIG_SIZE]);

/**
 * @brief Validates a compressed secp256k1 public key
 * @param pubkey The compressed public key to validate (33 bytes)
 * @return true if the pubkey is valid and on-curve, false otherwise
 */
bool crypto_ecc_secp256k1_pubkey_verify(const uint8_t pubkey[SECP256K1_SEC1_KEY_SIZE]);

/**
 * @brief Generates a cryptographically secure random scalar in range [1, n-1].
 *
 * This function uses rejection sampling (FIPS 186-5 A2.2) to generate a random
 * scalar suitable for use as an ECC private key or ephemeral nonce.
 * Currently supports P-256 only.
 *
 * @param[out] scalar_bytes  Output buffer for the random scalar (32 bytes).
 * @param[in]  scalar_size   Size of scalar buffer in bytes (must be 32 for P-256).
 *
 * @return true on success, false on failure (e.g., invalid size or RNG failure).
 *
 * @note This function will retry up to SECP256K1_KEYGEN_MAX_ATTEMPTS times to generate a valid
 * scalar.
 */
bool crypto_ecc_generate_random_scalar(uint8_t* scalar_bytes, size_t scalar_size);

/**
 * @brief Derives the public key from a private key.
 *
 * This function derives the public key from a private key using the elliptic curve
 * point multiplication operation.
 * Currently supports P-256 only.
 *
 * @param[in]  privkey       The private key to derive the public key from (32 bytes).
 * @param[out] public_key    The public key to store the result (64 bytes).
 *
 * @return true on success, false on failure (e.g., invalid size or curve parameter retrieval
 * failure).
 */
bool crypto_ecc_derive_public_key(key_handle_t* privkey, key_handle_t* public_key);
