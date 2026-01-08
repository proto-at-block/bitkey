/**
 * @file attestation.h
 * @brief Cryptographic attestation and device identity functions
 */

#pragma once

#include <stdbool.h>
#include <stdint.h>

/** Size of the device serial number in bytes */
#define CRYPTO_SERIAL_SIZE 8

/**
 * @brief Sign a challenge using the device's identity key
 *
 * @param[in] challenge Pointer to the challenge data to sign
 * @param[in] challenge_size Size of the challenge data in bytes
 * @param[out] signature Buffer to store the resulting signature
 * @param[in] signature_size Size of the signature buffer in bytes
 *
 * @return true if the challenge was successfully signed
 * @return false if signing failed
 */

bool crypto_sign_challenge(uint8_t* challenge, uint32_t challenge_size, uint8_t* signature,
                           uint32_t signature_size);

/**
 * @brief Read the device's unique serial number
 *
 * @param[out] serial_number Buffer to store the serial number.
 *                           Must be at least CRYPTO_SERIAL_SIZE bytes.
 *
 * @return true if the serial number was successfully read
 * @return false if reading failed
 */
bool crypto_read_serial(uint8_t* serial_number);
