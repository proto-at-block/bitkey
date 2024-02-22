#pragma once

#include "secutils.h"
#include "sl_se_manager.h"
#include "sl_se_manager_defines.h"
#include "sl_se_manager_types.h"
#include "sl_status.h"

#include <stdint.h>

// Based on SiLabs Secure Engine Manager API. See license in README.

// The naming convention in this library is intended to be:
// sl_se_* are unmodified Gecko SDK functions (typically, the whole Gekco SDK .c was dropped into
// our source tree)
// se_* are adapted from Gecko SDK

#define SE_AES_GCM_IV_LENGTH  (12)
#define SE_AES_GCM_TAG_LENGTH (16)

// AES
sl_status_t se_aes_gcm(sl_se_command_context_t* cmd_ctx, const sl_se_key_descriptor_t* key,
                       sl_se_cipher_operation_t mode, size_t length,
                       uint8_t iv[SE_AES_GCM_IV_LENGTH], const unsigned char* aad,
                       size_t aad_length, const unsigned char* input, unsigned char* output,
                       uint8_t tag[SE_AES_GCM_TAG_LENGTH]);

sl_status_t se_aes_cmac(sl_se_command_context_t* cmd_ctx, const sl_se_key_descriptor_t* key,
                        const unsigned char* input, size_t input_len, unsigned char* output);

sl_status_t se_aes_cbc(sl_se_command_context_t* cmd_ctx, const sl_se_key_descriptor_t* key,
                       sl_se_cipher_operation_t mode, size_t length, unsigned char iv[16],
                       const unsigned char* input, unsigned char* output);

sl_status_t se_aes_ecb(sl_se_command_context_t* cmd_ctx, const sl_se_key_descriptor_t* key,
                       sl_se_cipher_operation_t mode, size_t length, const unsigned char* input,
                       unsigned char* output);

// RNG
sl_status_t sl_se_get_random(sl_se_command_context_t* cmd_ctx, void* data, uint32_t num_bytes);

// Key management
sl_status_t sl_se_generate_key(sl_se_command_context_t* cmd_ctx,
                               const sl_se_key_descriptor_t* key_out);

// Key derivation
sl_status_t sl_se_derive_key_hkdf(sl_se_command_context_t* cmd_ctx,
                                  const sl_se_key_descriptor_t* in_key, sl_se_hash_type_t hash,
                                  const unsigned char* salt, size_t salt_len,
                                  const unsigned char* info, size_t info_len,
                                  sl_se_key_descriptor_t* out_key);

// Hash

sl_status_t se_hash(sl_se_command_context_t* cmd_ctx, sl_se_hash_type_t hash_type,
                    const uint8_t* message, unsigned int message_size, uint8_t* digest,
                    size_t digest_size);
sl_status_t se_hmac(sl_se_command_context_t* cmd_ctx, const sl_se_key_descriptor_t* key,
                    sl_se_hash_type_t hash_type, const uint8_t* message, size_t message_len,
                    uint8_t* output, size_t output_len);

sl_status_t se_hash_sha256_multipart_starts(sl_se_sha256_multipart_context_t* sha256_ctx,
                                            sl_se_command_context_t* cmd_ctx);
sl_status_t se_hash_multipart_update(void* hash_type_ctx, sl_se_command_context_t* cmd_ctx,
                                     const uint8_t* input, size_t input_len);
sl_status_t se_hash_multipart_finish(void* hash_type_ctx, sl_se_command_context_t* cmd_ctx,
                                     uint8_t* digest_out, size_t digest_len);

// Key derivation
sl_status_t sl_se_ecdh_compute_shared_secret(sl_se_command_context_t* cmd_ctx,
                                             const sl_se_key_descriptor_t* key_in_priv,
                                             const sl_se_key_descriptor_t* key_in_pub,
                                             const sl_se_key_descriptor_t* key_out);

sl_status_t sl_se_derive_key_hkdf(sl_se_command_context_t* cmd_ctx,
                                  const sl_se_key_descriptor_t* in_key, sl_se_hash_type_t hash,
                                  const unsigned char* salt, size_t salt_len,
                                  const unsigned char* info, size_t info_len,
                                  sl_se_key_descriptor_t* out_key);

// Asymmetric

sl_status_t se_ecc_verify(sl_se_command_context_t* cmd_ctx, const sl_se_key_descriptor_t* key,
                          sl_se_hash_type_t hash_alg, bool hashed_message,
                          const unsigned char* message, size_t message_len,
                          const unsigned char* signature, size_t signature_len);

sl_status_t se_ecc_sign(sl_se_command_context_t* cmd_ctx, const sl_se_key_descriptor_t* key,
                        sl_se_hash_type_t hash_alg, bool hashed_message,
                        const unsigned char* message, size_t message_len,
                        const unsigned char* signature, size_t signature_len);

// Attestation

sl_status_t se_sign_with_device_identity_key(uint8_t* data, uint32_t size, uint8_t* signature,
                                             uint32_t signature_size);

sl_status_t se_sign_challenge(uint8_t* challenge, uint32_t challenge_size, uint8_t* signature,
                              uint32_t signature_size);

// Tamper

sl_status_t se_configure_active_mode(secure_bool_t enter);

// Utilities, e.g. reading fuses

#define SL_SE_CERT_BATCH       0x01
#define SL_SE_CERT_DEVICE_SE   0x02
#define SL_SE_CERT_DEVICE_HOST 0x03

#define SE_PUBKEY_SIZE (64)

#define SE_SERIAL_SIZE         (16)
#define SE_ACTUAL_SERIAL_SIZE  (8)
#define SE_ACTUAL_SERIAL_START (8)

typedef struct {
  uint32_t version;
  uint32_t otp_version;
  uint8_t serial[16];
  sl_se_otp_init_t otp;
  sl_se_status_t se_status;
} se_info_t;

typedef struct {
  uint8_t* boot;
  uint8_t* auth;
  uint8_t* attestation;
  uint8_t* se_attestation;
} se_pubkeys_t;

// IMPORTANT: Must match fwpb_secure_boot_config.
typedef enum {
  SECURE_BOOT_CONFIG_INVALID,
  SECURE_BOOT_CONFIG_DEV,
  SECURE_BOOT_CONFIG_PROD,
} secure_boot_config_t;

sl_status_t se_get_secinfo(se_info_t* info);
sl_status_t se_get_status(sl_se_status_t* se_status);
sl_status_t se_read_cert(sl_se_cert_type_t kind, uint8_t cert[512], uint16_t* size);
sl_status_t se_read_pubkeys(se_pubkeys_t* pubkeys);
sl_status_t se_read_pubkey(sl_se_device_key_type_t kind, uint8_t* pubkey, uint32_t size);
sl_status_t se_get_secure_boot_config(secure_boot_config_t* config);

// The SE serial size is technically 16 bytes. But, the first 8 bytes are always zero.
sl_status_t se_read_serial(uint8_t serial[SE_SERIAL_SIZE]);
