/**
 * @file
 *
 * @brief Secure Channel Certificate API
 *
 * @details API for secure channel certificate operations.
 *
 * @{
 */
#pragma once

#include "aes.h"
#include "ecc.h"
#include "filesystem.h"
#include "key_management.h"
#include "picocert.h"
#include "rtos.h"
#include "uxc.pb.h"
#include "wallet.pb.h"

#include <stdbool.h>
#include <stdint.h>

// Maximum buffer size for certificate ID (includes null terminator)
#define SC_CERT_ID_MAX_SIZE PICOCERT_MAX_NAME_LEN
#define SC_CERT_DIRECTORY   "/secure_channel"

// ECC public key format prefix for SEC1 uncompressed format (0x04 || X || Y)
#define ECC_PUBKEY_SEC1_UNCOMPRESSED_PREFIX 0x04

_Static_assert(((sizeof(SC_CERT_DIRECTORY) - 1) + SC_CERT_ID_MAX_SIZE + (sizeof("/.cert") - 1) +
                1) <= FS_FILE_NAME_MAX_LEN,
               "Secure channel certificate file path may exceed maximum filename length");

typedef enum {
  CERT_TYPE_UNSUPPORTED = 0,
  CERT_TYPE_PICOCERT = 1,
  CERT_TYPE_MAX_TYPES,  // Sentinel: must be last, equals number of valid types
} secure_channel_cert_type_t;

typedef enum {
  SECURE_CHANNEL_CERT_OK = 0,
  SECURE_CHANNEL_CERT_ERROR = 1,
  SECURE_CHANNEL_CERT_PINNED_MISMATCH = 2,
  SECURE_CHANNEL_CERT_PINNED_NOT_FOUND = 3,
  SECURE_CHANNEL_CERT_UNSUPPORTED_TYPE = 4,
  SECURE_CHANNEL_CERT_PINNED_ALREADY_EXISTS = 5,
} secure_channel_cert_err_t;

/**
 * @brief Certificate data with type discriminator
 */
typedef struct {
  secure_channel_cert_type_t type;
  union {
    picocert_t picocert;
  } data;
} secure_channel_cert_data_t;

typedef struct {
  rtos_mutex_t mutex;
} secure_channel_cert_priv_t;

typedef struct {
  // The certificate ID identifies which cert to use to establish identity.
  // Depending on the configuration, it's possible to use one cert for multiple channels
  // or to have unique certs for each channel, etc.
  char id[SC_CERT_ID_MAX_SIZE];

  // key_management supports a variety of key types, including enclave managed.
  // these settings describe the key to use for the certificate.
  // these settings are used to load the local certificate key at runtime.
  key_algorithm_t key_type;
  key_storage_type_t key_storage_type;

  // loaded certificate type
  secure_channel_cert_type_t cert_type;
} secure_channel_cert_desc_t;

// Each product defines its own secure_channel_product_certs array in their secure_channel_certs.c
// file.
extern const secure_channel_cert_desc_t* const secure_channel_product_certs[];

/**
 * @brief Initialize certificate context and generate/load LOCAL certificates
 *
 * Automatically uses secure_channel_product_certs for the platform.
 */
void secure_channel_cert_init(void);

/**
 * @brief Read a certificate by its ID (subject)
 * @param cert_id Certificate ID/subject (e.g., "w3_core_id", "w3_uxc_id")
 * @param cert_data_out Output buffer for certificate data
 * @return true if found, false if not found or error
 */
bool secure_channel_read_cert(const char* cert_id, secure_channel_cert_data_t* cert_data_out);

/**
 * @brief Sign data using a certificate's private key
 *
 * @param cert_desc Descriptor of the certificate whose key should be used
 * @param digest The digest to sign
 * @param digest_size Size of the digest (must be SHA256_DIGEST_SIZE)
 * @param signature Output buffer for signature (must be ECC_SIG_SIZE)
 * @param signature_size Size of signature buffer
 * @return true on success, false on error
 */
bool secure_channel_sign_digest(const secure_channel_cert_desc_t* cert_desc, const uint8_t* digest,
                                const uint32_t digest_size, uint8_t* signature,
                                uint32_t signature_size);

/**
 * @brief Pin a peer certificate for Trust-on-First-Use (TOFU) security
 *
 * This stores the full peer certificate for future verification.
 * Call this only once per peer, typically after the first successful handshake.
 *
 * @param cert_data The certificate to pin (must have type field set)
 * @return SECURE_CHANNEL_CERT_OK if pinned successfully,
 *         SECURE_CHANNEL_CERT_PINNED_ALREADY_EXISTS if a certificate is already pinned,
 *         SECURE_CHANNEL_CERT_ERROR on failure,
 *         SECURE_CHANNEL_CERT_UNSUPPORTED_TYPE if cert type is not supported
 */
secure_channel_cert_err_t secure_channel_pin_cert(const secure_channel_cert_data_t* cert_data);

/**
 * @brief Check if a certificate matches the stored pin
 *
 * This checks if the provided certificate matches the previously-pinned certificate
 * for this peer. Does not modify any state - pure verification.
 *
 * @param cert_data The certificate to check against the pin
 * @return SECURE_CHANNEL_CERT_OK if cert matches the stored pin,
 *         SECURE_CHANNEL_CERT_PINNED_MISMATCH if cert doesn't match pin,
 *         SECURE_CHANNEL_CERT_PINNED_NOT_FOUND if pin doesn't exist,
 *         SECURE_CHANNEL_CERT_ERROR if other error,
 *         SECURE_CHANNEL_CERT_UNSUPPORTED_TYPE if cert type is not supported
 */
secure_channel_cert_err_t secure_channel_matches_pinned_cert(
  const secure_channel_cert_data_t* cert_data);

/**
 * @brief Verify a signature on a digest using a certificate's public key
 *
 * This verifies that a signature is valid for the given digest using the
 * certificate's public key. Does not check pinning - use verify_pin separately.
 *
 * @param cert_data Certificate containing the public key
 * @param digest Hash of the data to verify
 * @param digest_size Size of the digest (must be SHA256_DIGEST_SIZE)
 * @param signature Signature to verify
 * @param signature_size Size of the signature (must be ECC_SIG_SIZE)
 * @return true if signature is valid, false otherwise
 */
bool secure_channel_verify_digest(const secure_channel_cert_data_t* cert_data,
                                  const uint8_t* digest, uint32_t digest_size,
                                  const uint8_t* signature, uint32_t signature_size);

/**
 * @brief Handle a CERT_GET command
 * @param cmd Command to handle
 * @param rsp Response to return
 * @return true if successful, false otherwise
 */
bool secure_channel_cert_handle_cmd_get(fwpb_cert_get_cmd* cmd, fwpb_cert_get_rsp* rsp);
/** @} */
