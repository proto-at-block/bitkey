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

secure_bool_t crypto_ecc_verify_hash(key_handle_t* key, uint8_t* hash, uint32_t hash_size,
                                     uint8_t signature[ECC_SIG_SIZE]);

// Does NOT verify the signature after signing. If that is important for the security of
// the caller, the caller must verify the signature.
secure_bool_t crypto_ecc_sign_hash(key_handle_t* privkey, uint8_t* hash, uint32_t hash_size,
                                   uint8_t signature[ECC_SIG_SIZE]);

bool crypto_ecc_validate_private_key(key_handle_t* privkey);

// secp256k1

#define SECP256K1_KEY_SIZE      (32u)
#define SECP256K1_KEYPAIR_SIZE  (96u)
#define SECP256K1_SEC1_KEY_SIZE (33u)

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
