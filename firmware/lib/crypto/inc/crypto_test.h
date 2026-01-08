/**
 * @file
 *
 * @brief Crypto Test Suite
 *
 * @details Suite of crypto tests for validating MCU cryptographic operations.
 *
 * @{
 */

#pragma once

/**
 * @brief Runs the crypto ECDSA test suite.
 */
void crypto_test_ecdsa(void);

/**
 * @brief Runs dudero to test rng
 */
void crypto_test_random(void);

/**
 * @brief Runs DRBG tests
 */
void crypto_test_drbg(void);

/**
 * @brief Runs curve 25519 ecdh tests
 */
void crypto_test_curve25519(void);

/**
 * @brief Runs HKDF tests
 */
void crypto_test_hkdf(void);

/**
 * @brief Runs GCM tests
 *
 */
void crypto_test_gcm(void);
/** @} */
