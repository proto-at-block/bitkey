#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define SIM_CERT_MAX_SIZE 512

/**
 * @file sim_provisioning.h
 * @brief Runtime provisioning for core-sim attestation testing
 *
 * When CORE_SIM_PROVISION=1 environment variable is set, the simulator will
 * generate a device identity at startup (P-256 keypair, serial number, and
 * X.509 certificate signed by the dev batch key).
 *
 * This enables testing of attestation flows without real hardware.
 */

/**
 * Initialize provisioning if CORE_SIM_PROVISION env var is set.
 *
 * If enabled, this will:
 * 1. Generate a random 8-byte serial number
 * 2. Generate a P-256 keypair for device identity
 * 3. Create an X.509 device certificate signed by the dev batch key
 *
 * @return true if provisioning was enabled and succeeded
 * @return false if provisioning is disabled or failed
 */
bool sim_provision_init(void);

/**
 * Check if provisioning is active.
 *
 * @return true if sim_provision_init() succeeded
 * @return false otherwise
 */
bool sim_is_provisioned(void);

/**
 * Get the device certificate (X.509 DER format).
 *
 * @param out_len Output parameter for certificate length
 * @return Pointer to DER-encoded certificate, or NULL if not provisioned
 */
const uint8_t* sim_get_device_cert(size_t* out_len);

/**
 * Get the batch certificate (X.509 DER format).
 *
 * @param out_len Output parameter for certificate length
 * @return Pointer to DER-encoded certificate
 */
const uint8_t* sim_get_batch_cert(size_t* out_len);

/**
 * Get the factory certificate (X.509 DER format).
 *
 * @param out_len Output parameter for certificate length
 * @return Pointer to DER-encoded certificate
 */
const uint8_t* sim_get_factory_cert(size_t* out_len);

/**
 * Get the root certificate (X.509 DER format).
 *
 * @param out_len Output parameter for certificate length
 * @return Pointer to DER-encoded certificate
 */
const uint8_t* sim_get_root_cert(size_t* out_len);

/**
 * Get the device's P-256 public key.
 *
 * @return Pointer to 64-byte public key (32-byte X || 32-byte Y), or NULL if not provisioned
 */
const uint8_t* sim_get_device_pubkey(void);

/**
 * Get the device's P-256 private key.
 *
 * @return Pointer to 32-byte private key, or NULL if not provisioned
 */
const uint8_t* sim_get_device_privkey(void);

/**
 * Get the device serial number.
 *
 * @return Pointer to 8-byte serial number, or NULL if not provisioned
 */
const uint8_t* sim_get_serial(void);

/**
 * Sign a digest with the device identity key using ECDSA P-256.
 *
 * IMPORTANT: Returns raw 64-byte R||S signature format (NOT DER-encoded).
 * This matches what Ring's ECDSA_P256_SHA256_FIXED and PyCryptodome's DSS expect.
 *
 * @param digest SHA-256 digest to sign (32 bytes)
 * @param digest_len Length of digest (should be 32)
 * @param sig_out Output buffer for signature
 * @param sig_max_len Size of output buffer (must be >= 64)
 * @return Actual signature length (64 on success), or 0 on failure
 */
size_t sim_sign_with_device_key(const uint8_t* digest, size_t digest_len, uint8_t* sig_out,
                                size_t sig_max_len);

/**
 * Wipe provisioning state (clear in-memory and delete persisted identity).
 *
 * Called during device wipe to remove the device identity.
 * After calling this, sim_is_provisioned() will return false and
 * CORE_SIM_PROVISION=1 will be required to generate a new identity.
 *
 * @return true if wipe succeeded
 */
bool sim_provision_wipe(void);
