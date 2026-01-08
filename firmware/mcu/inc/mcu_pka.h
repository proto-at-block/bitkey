/**
 * @file
 *
 * @brief MCU Public-Key Hardware Accelerator
 *
 * ## Overview
 *
 * The MCU PKA module provides hardware-accelerated elliptic curve cryptography
 * (ECC) operations. The primary use case is ECDSA signature generation and
 * verification.
 *
 * ## Features
 *
 * ### Supported Curves
 *
 * 1. secp256r1 (NIST P-256)
 * 2. secp256k1
 * 3. secp384r1 (NIST P-384)
 *
 * ### Generic APIs
 *
 * 1. Point Validation
 * 2. Point Multiplication
 *
 * ## Usage
 *
 * ### Initializing the PKA
 *
 * ```c
 * mcu_pka_init();
 * ```
 *
 * ### Verifying a Signature
 *
 * ```c
 * // Message to verify
 * const uint8_t message[] = "You think this is slicked back? This is pushed back.";
 * uint8_t message_hash[32];
 *
 * // Hash the message with SHA-256
 * mcu_err_t err;
 * err = mcu_hash(MCU_HASH_ALG_SHA256, message, sizeof(message) - 1,
 *                message_hash, sizeof(message_hash));
 * ASSERT(err == MCU_ERROR_OK);
 *
 * // Get P-256 curve parameters
 * mcu_pka_curve_params_t curve_params;
 * err = mcu_pka_get_curve_params(MCU_PKA_CURVE_SECP256R1, &curve_params);
 * ASSERT(err == MCU_ERROR_OK);
 *
 * // Setup public key and signature
 * const uint8_t pubkey_x[32] = { 0x00 };
 * const uint8_t pubkey_y[32] = { 0x00 };
 * mcu_pka_public_key_t public_key = {
 *   .x = pubkey_x,
 *   .y = pubkey_y,
 *   .size = 32
 * };
 *
 * // Signature (r, s components in big-endian)
 * const uint8_t sig_r[32] = { 0x00 };
 * const uint8_t sig_s[32] = { 0x00 };
 * mcu_pka_signature_t signature = {
 *   .r = sig_r,
 *   .s = sig_s,
 *   .size = 32
 * };
 *
 * // Verify signature
 * err = mcu_pka_ecdsa_verify(&curve_params, &public_key,
 *   message_hash, sizeof(message_hash), &signature);
 *
 * if (err == MCU_ERROR_OK) {
 *   // Signature is valid
 * } else {
 *   // Signature is invalid
 * }
 * ```
 *
 * @{
 */

#pragma once

#include "mcu.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/**
 * @brief Maximum supported key size in bytes (for P-521).
 */
#define MCU_PKA_MAX_KEY_SIZE (66u)

/**
 * @brief Size of secp256r1 (P-256) curve parameters in bytes.
 */
#define MCU_PKA_SECP256R1_SIZE (32u)

/**
 * @brief Size of secp256k1 curve parameters in bytes.
 */
#define MCU_PKA_SECP256K1_SIZE (32u)

/**
 * @brief Size of secp384r1 (P-384) curve parameters in bytes.
 */
#define MCU_PKA_SECP384R1_SIZE (48u)

/**
 * @brief Elliptic curve types supported by the PKA module.
 */
typedef enum {
  /**
   * @brief NIST P-256 (secp256r1) curve.
   */
  MCU_PKA_CURVE_SECP256R1 = 0,

  /**
   * @brief secp256k1 curve (used in Bitcoin).
   */
  MCU_PKA_CURVE_SECP256K1 = 1,

  /**
   * @brief NIST P-384 (secp384r1) curve.
   */
  MCU_PKA_CURVE_SECP384R1 = 2,

  /**
   * @brief Custom curve (user-provided parameters).
   */
  MCU_PKA_CURVE_CUSTOM = 3,
} mcu_pka_curve_t;

/**
 * @brief Elliptic curve parameters.
 *
 * For standard curves, use predefined parameters via @ref mcu_pka_get_curve_params.
 */
typedef struct {
  /**
   * @brief Curve prime modulus p (big-endian).
   */
  const uint8_t* modulus;

  /**
   * @brief Curve coefficient a (big-endian).
   */
  const uint8_t* coef_a;

  /**
   * @brief Curve coefficient b (big-endian).
   */
  const uint8_t* coef_b;

  /**
   * @brief Base point x coordinate (big-endian).
   */
  const uint8_t* base_point_x;

  /**
   * @brief Base point y coordinate (big-endian).
   */
  const uint8_t* base_point_y;

  /**
   * @brief Curve order n (big-endian).
   */
  const uint8_t* order;

  /**
   * @brief Size of modulus in bytes.
   */
  uint32_t modulus_size;

  /**
   * @brief Size of order in bytes.
   */
  uint32_t order_size;

  /**
   * @brief Sign of coefficient a (0 = positive, 1 = negative).
   */
  uint32_t coef_a_sign;
} mcu_pka_curve_params_t;

/**
 * @brief ECDSA signature.
 */
typedef struct {
  /**
   * @brief Signature component r (big-endian).
   */
  const uint8_t* r;

  /**
   * @brief Signature component s (big-endian).
   */
  const uint8_t* s;

  /**
   * @brief Size of r and s in bytes.
   */
  uint32_t size;
} mcu_pka_signature_t;

/**
 * @brief ECC public key.
 */
typedef struct {
  /**
   * @brief Public key x coordinate (big-endian).
   */
  const uint8_t* x;

  /**
   * @brief Public key y coordinate (big-endian).
   */
  const uint8_t* y;

  /**
   * @brief Size of x and y coordinates in bytes.
   */
  uint32_t size;
} mcu_pka_public_key_t;

/**
 * @brief Initializes the PKA module.
 *
 * This function must be called before using any PKA operations.
 */
void mcu_pka_init(void);

/**
 * @brief Gets predefined curve parameters for standard curves.
 *
 * @param[in]  curve   The elliptic curve type.
 * @param[out] params  Output structure to receive curve parameters.
 *
 * @return #MCU_ERROR_OK on success, #MCU_ERROR_PARAMETER if curve is not supported.
 */
mcu_err_t mcu_pka_get_curve_params(mcu_pka_curve_t curve, mcu_pka_curve_params_t* params);

/**
 * @brief Generates an ECDSA signature.
 *
 * This function signs a message hash using the provided private key on the
 * specified elliptic curve.
 *
 * @param[in]  curve_params  Elliptic curve parameters.
 * @param[in]  private_key   Private key scalar d (big-endian, same size as curve order).
 * @param[in]  hash          Hash of the message to sign (big-endian, same size as curve order).
 * @param[in]  hash_size     Size of the hash in bytes (must be <= order size).
 * @param[in]  k             Ephemeral random value k (big-endian, same size as curve order).
 * @param[out] signature_r   Output buffer for signature component r (same size as curve order).
 * @param[out] signature_s   Output buffer for signature component s (same size as curve order).
 *
 * @return #MCU_ERROR_OK on success, #MCU_ERROR_PARAMETER for invalid parameters,
 *         or #MCU_ERROR_PKA_FAIL if signing fails.
 *
 * @note This function blocks until the signing is complete.
 *
 * @note All byte arrays (private_key, hash, k, signature_r, signature_s) must be
 *       exactly curve_params->order_size bytes in length.
 *
 * @note The ephemeral value k must be cryptographically secure random and unique
 *       for each signature. Reusing k values will compromise the private key.
 *
 * @note All multi-byte values must be in big-endian format.
 */
mcu_err_t mcu_pka_ecdsa_sign(const mcu_pka_curve_params_t* curve_params, const uint8_t* private_key,
                             const uint8_t* hash, size_t hash_size, const uint8_t* k,
                             uint8_t* signature_r, uint8_t* signature_s);

/**
 * @brief Verifies an ECDSA signature.
 *
 * This function verifies that a signature is valid for the given message hash
 * and public key on the specified elliptic curve.
 *
 * @param[in] curve_params  Elliptic curve parameters.
 * @param[in] public_key    Public key to verify against.
 * @param[in] hash          Hash of the message (big-endian, same size as curve order).
 * @param[in] hash_size     Size of the hash in bytes (must be <= order size).
 * @param[in] signature     ECDSA signature (r, s).
 *
 * @return #MCU_ERROR_OK if signature is valid, #MCU_ERROR_PARAMETER for invalid
 *         parameters, or #MCU_ERROR_PKA_FAIL if signature check fails.
 *
 * @note This function blocks until the verification is complete.
 *
 * @note The hash must be the same size as the curve order.
 *
 * @note All multi-byte values must be in big-endian format.
 */
mcu_err_t mcu_pka_ecdsa_verify(const mcu_pka_curve_params_t* curve_params,
                               const mcu_pka_public_key_t* public_key, const uint8_t* hash,
                               size_t hash_size, const mcu_pka_signature_t* signature);

/**
 * @brief Verifies a point is on the elliptic curve.
 *
 * @param[in] curve_params  Elliptic curve parameters.
 * @param[in] point_x       Point x coordinate (big-endian).
 * @param[in] point_y       Point y coordinate (big-endian).
 *
 * @return #MCU_ERROR_OK if point is on curve, error otherwise.
 */
mcu_err_t mcu_pka_point_check(const mcu_pka_curve_params_t* curve_params, const uint8_t* point_x,
                              const uint8_t* point_y);

/**
 * @brief Performs elliptic curve point multiplication (scalar multiplication).
 *
 * Computes Q = k * P where P is a point on the curve and k is a scalar.
 *
 * @param[in]  curve_params  Elliptic curve parameters.
 * @param[in]  scalar        Scalar multiplier k (big-endian).
 * @param[in]  scalar_size   Size of scalar in bytes.
 * @param[in]  point_x       Input point P x coordinate (big-endian).
 * @param[in]  point_y       Input point P y coordinate (big-endian).
 * @param[out] result_x      Output point Q x coordinate (big-endian).
 * @param[out] result_y      Output point Q y coordinate (big-endian).
 *
 * @return #MCU_ERROR_OK on success, error otherwise.
 */
mcu_err_t mcu_pka_ecc_mul(const mcu_pka_curve_params_t* curve_params, const uint8_t* scalar,
                          size_t scalar_size, const uint8_t* point_x, const uint8_t* point_y,
                          uint8_t* result_x, uint8_t* result_y);

/**
 * @brief Validates that a scalar is in the valid range [1, n-1] for ECC operations.
 *
 * This function checks that a scalar value (private key or ephemeral nonce) is:
 * - Not zero
 * - Less than the curve order n
 *
 * @param[in] curve_params  Elliptic curve parameters.
 * @param[in] scalar        Scalar value to validate (big-endian).
 * @param[in] scalar_size   Size of scalar in bytes.
 *
 * @return #MCU_ERROR_OK if scalar is valid, #MCU_ERROR_PARAMETER otherwise.
 *
 * @note This should be called to validate private keys and ephemeral nonces (k)
 *       before use in ECC operations.
 */
mcu_err_t mcu_pka_validate_scalar(const mcu_pka_curve_params_t* curve_params, const uint8_t* scalar,
                                  size_t scalar_size);

/** @} */
