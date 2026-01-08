/**
 * @file curve25519.h
 * @brief Curve25519 elliptic curve cryptography operations
 *
 * This file provides functions for Curve25519 key operations, including
 * public key derivation and shared secret computation for Diffie-Hellman
 * key exchange.
 */

#pragma once

#include <stdbool.h>
#include <stdint.h>

/**
 * @brief Length of Curve25519 keys in bytes
 */
#define CURVE25519_KEY_LEN 32

/**
 * @brief Derive a public key from a private key
 *
 * Computes the Curve25519 public key corresponding to the given private key
 *
 * @param[out] pk Buffer to store the computed public key (must be CURVE25519_KEY_LEN bytes)
 * @param[in] sk Private key (must be CURVE25519_KEY_LEN bytes)
 */
void curve25519_get_public_key(uint8_t* pk, const uint8_t* sk);

/**
 * @brief Compute a shared secret
 *
 * @param[out] shared Buffer to store the computed shared secret (must be CURVE25519_KEY_LEN bytes)
 * @param[in] my_sk Local private key (must be CURVE25519_KEY_LEN bytes)
 * @param[in] their_pk Remote public key (must be CURVE25519_KEY_LEN bytes)
 */
void curve25519_get_shared_secret(uint8_t* shared, const uint8_t* my_sk, const uint8_t* their_pk);

/**
 * @brief Generate a random Curve25519 private key
 *
 * Generates a cryptographically secure random private key suitable for X25519
 * key exchange. The key is automatically clamped according to RFC 7748:
 * - Bits 0-2 are cleared (key_bytes[0] &= 248)
 * - Bit 255 is cleared (key_bytes[31] &= 127)
 * - Bit 254 is set (key_bytes[31] |= 64)
 *
 * @param[out] key_bytes Buffer to store the generated private key
 * @param[in] key_len Length of the key buffer (must be CURVE25519_KEY_LEN)
 *
 * @return true if the key was successfully generated
 * @return false if key generation failed (e.g., RNG failure)
 */
bool curve25519_generate_key(uint8_t* key_bytes, uint32_t key_len);
