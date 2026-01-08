/**
 * @file
 *
 * @brief MCU Hash
 *
 * @{
 */

#pragma once

#include "mcu.h"

#include <stddef.h>
#include <stdint.h>

/**
 * @brief Number of bytes in a SHA1 digest.
 */
#define MCU_HASH_SHA1_DIGEST_LENGTH (20u)

/**
 * @brief Number of bytes in a SHA256 digest.
 */
#define MCU_HASH_SHA256_DIGEST_LENGTH (32u)

/**
 * @brief Number of bytes in an MD5 digest.
 */
#define MCU_HASH_MD5_DIGEST_LENGTH (16u)

/**
 * @brief Number of bytes in the HMAC signature output for SHA-256.
 */
#define MCU_HASH_HMAC_SIGNATURE_SHA256_LENGTH (32u)

/**
 * @brief Algorithms supported by the hashing APIs..
 */
typedef enum {
  /**
   * @brief Unused.
   */
  MCU_HASH_ALG_NONE = 0,

  /**
   * @brief SHA1.
   */
  MCU_HASH_ALG_SHA1 = 1,

  /**
   * @brief SHA256.
   */
  MCU_HASH_ALG_SHA256 = 2,

  /**
   * @brief MD5.
   */
  MCU_HASH_ALG_MD5 = 3,
} mcu_hash_alg_t;

/**
 * @brief Initializes the hash module.
 */
void mcu_hash_init(void);

/**
 * @brief Performs a hash computation.
 *
 * @param[in]  alg          The MCU hashing algorithm.
 * @param[in]  data         Pointer to the data to process.
 * @param[in]  data_size    Length of the @p data in bytes.
 * @param[out] digest       Output buffer to store the digest.
 * @param[in]  digest_size  Length of @p digest in bytes.
 *
 * @return `MCU_ERROR_OK` on success, otherwise an error as defined in `mcu_err_t`.
 */
mcu_err_t mcu_hash(mcu_hash_alg_t alg, const uint8_t* data, size_t data_size, uint8_t* digest,
                   size_t digest_size);

/**
 * @brief Starts a new hash computation.
 *
 * @param alg  The MCU hashing algorithm.
 *
 * @return `MCU_ERROR_OK` on success, otherwise an error as defined in `mcu_err_t`.
 *
 * @note The calling thread *MUST* call #mcu_hash_finish() to complete the
 * computation and release the hash module for use by other threads.
 */
mcu_err_t mcu_hash_start(mcu_hash_alg_t alg);

/**
 * @brief Ingests data into the hash module.
 *
 * @param data       Pointer to the data to process.
 * @param data_size  Length of the @p data in bytes.
 *
 * @return `MCU_ERROR_OK` on success, otherwise an error as defined in `mcu_err_t`.
 */
mcu_err_t mcu_hash_update(const uint8_t* data, size_t data_size);

/**
 * @brief Finishes the current hash operation, retrieving the digest in the
 * @p digest output argument.
 *
 * @param[out] digest       Output buffer to store the digest.
 * @param[in]  digest_size  Length of @p digest in bytes.
 *
 * @return `MCU_ERROR_OK` on success, otherwise an error as defined in `mcu_err_t`.
 */
mcu_err_t mcu_hash_finish(uint8_t* digest, size_t digest_size);

/**
 * @brief Computes the HMAC (Hash-based Message Authentication Code).
 *
 * @param[in]  alg             The hashing algorithm.
 * @param[in]  message         The message to be hashed.
 * @param[in]  message_size    Length of the @p message in bytes.
 * @param[in]  key             Secret key bytes.
 * @param[in]  key_size        Length of the @p key in bytes.
 * @param[out] signature       HMAC signature.
 * @param[in]  signature_size  Length of @p signature in bytes.
 *
 * @return `MCU_ERROR_OK` on success, otherwise an error as defined in `mcu_err_t`.
 */
mcu_err_t mcu_hash_hmac(mcu_hash_alg_t alg, const uint8_t* message, size_t message_size,
                        const uint8_t* key, size_t key_size, uint8_t* signature,
                        size_t signature_size);

/** @} */
