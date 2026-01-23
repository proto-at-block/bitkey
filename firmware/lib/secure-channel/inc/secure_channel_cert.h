#pragma once

#include "aes.h"
#include "ecc.h"
#include "key_management.h"
#include "picocert.h"
#include "rtos.h"

#include <stdbool.h>
#include <stdint.h>

#define SC_CERT_ID_MAX_LEN (32)

typedef enum {
  CERT_TYPE_PICOCERT = 0,
} secure_channel_cert_type_t;

typedef enum {
  SECURE_CHANNEL_CERT_OK = 0,
  SECURE_CHANNEL_CERT_ERROR = 1,
} secure_channel_cert_err_t;

typedef union {
  picocert_t picocert;
} secure_channel_cert_data_t;

/**
 * @brief Secure channel certificate.
 *
 * This structure contains the certificate and key data.
 */
typedef struct {
  // The certificate ID identifies which cert to use to establish identity.
  // Depending on the configuration, it's possible to use one cert for multiple channels
  // or to have unique certs for each channel, etc.
  char id[SC_CERT_ID_MAX_LEN];

  // key_management supports a variety of key types, including enclave managed.
  // these settings describe the key to use for the certificate.
  // these settings are used to load the local certificate key at runtime.
  key_algorithm_t key_type;
  key_storage_type_t key_storage_type;

  // loaded certificate type
  secure_channel_cert_type_t cert_type;
} secure_channel_cert_desc_t;

/**
 * @brief Secure channel certificate context.
 *
 * Each secure_channel_t has its own certificate context.
 */
typedef struct {
  // Constant descriptors for the local and peer certificates.
  // Supports different certificate types and key types for the same channels.
  const secure_channel_cert_desc_t* local_cert_desc;
  const secure_channel_cert_desc_t* peer_cert_desc;

  // Local certificate and key material is always generated/loaded at runtime
  // from the local_cert_desc.
  secure_channel_cert_data_t local_cert_data;
  key_handle_t local_key;

  // Peer certificate and key material is not always available at runtime.
  // It's possible peer certificates may not exist yet.
  bool peer_cert_loaded;
  secure_channel_cert_data_t peer_cert_data;

  // Per-channel safety
  rtos_mutex_t lock;
} secure_channel_cert_ctx_t;

// Create channel descriptors for W3-UXC <-> W3-Core secure channel.
// For now, set both to use ECDSA P-256 keys with picocerts.
// It's possible to update to the w3_core device identity certificate in the future by changing
// these parameters.
extern const secure_channel_cert_desc_t w3_uxc_identity_cert;
extern const secure_channel_cert_desc_t w3_core_identity_cert;

void secure_channel_cert_init(secure_channel_cert_ctx_t* ctx);

bool secure_channel_cert_sign(secure_channel_cert_ctx_t* ctx, const uint8_t* digest,
                              const uint32_t digest_size, uint8_t* signature,
                              uint32_t signature_size);

bool secure_channel_cert_verify(secure_channel_cert_ctx_t* ctx, const uint8_t* digest,
                                const uint32_t digest_size, const uint8_t* signature,
                                const uint32_t signature_size, secure_bool_t* verify_result);

bool secure_channel_cert_write(secure_channel_cert_ctx_t* ctx,
                               const secure_channel_cert_data_t* cert_data);

bool secure_channel_cert_read(const secure_channel_cert_desc_t* cert_desc,
                              secure_channel_cert_data_t* output_cert_data);
