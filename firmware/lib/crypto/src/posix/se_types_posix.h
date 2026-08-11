/**
 * @file se_types_posix.h
 * @brief POSIX-compatible definitions of Gecko SDK SE types.
 *
 * This header provides type compatibility with the Silicon Labs SE Manager API
 * for POSIX builds. On EFR32, these types come from the Gecko SDK headers
 * (em_se.h, sl_se_manager_types.h, etc.). For POSIX, we provide minimal
 * compatible definitions.
 *
 * This file is included by the shared HAL secure_engine.h when building for POSIX.
 */

#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

// =============================================================================
// Status codes (sl_status.h compatibility)
// =============================================================================

typedef uint32_t sl_status_t;
#define SL_STATUS_OK                 ((sl_status_t)0x0000)
#define SL_STATUS_FAIL               ((sl_status_t)0x0001)
#define SL_STATUS_NOT_AVAILABLE      ((sl_status_t)0x000B)
#define SL_STATUS_INVALID_PARAMETER  ((sl_status_t)0x0021)
#define SL_STATUS_INVALID_SIGNATURE  ((sl_status_t)0x0022)
#define SL_STATUS_COMMAND_IS_INVALID ((sl_status_t)0x0023)

// =============================================================================
// Key type definitions (sl_se_manager_defines.h compatibility)
// =============================================================================

typedef uint32_t sl_se_key_type_t;

// Symmetric key types
#define SL_SE_KEY_TYPE_SYMMETRIC 0x00000000
#define SL_SE_KEY_TYPE_AES_128   0x00000010
#define SL_SE_KEY_TYPE_AES_192   0x00000018
#define SL_SE_KEY_TYPE_AES_256   0x00000020

// ECC key type algorithm masks
#define SL_SE_KEY_TYPE_ALGORITHM_MASK   0xf0000000
#define SL_SE_KEY_TYPE_ALGORITHM_OFFSET 28
#define SL_SE_KEY_TYPE_ATTRIBUTES_MASK  0x00007fff

// ECC curve types
#define SL_SE_KEY_TYPE_ECC_WEIERSTRASS_PRIME_CUSTOM (0x8U << SL_SE_KEY_TYPE_ALGORITHM_OFFSET)
#define SL_SE_KEY_TYPE_ECC_MONTGOMERY               (0xbU << SL_SE_KEY_TYPE_ALGORITHM_OFFSET)
#define SL_SE_KEY_TYPE_ECC_EDDSA                    (0xcU << SL_SE_KEY_TYPE_ALGORITHM_OFFSET)

// Standard curves
#define SL_SE_KEY_TYPE_ECC_P256    (SL_SE_KEY_TYPE_ECC_WEIERSTRASS_PRIME_CUSTOM | 0x20)
#define SL_SE_KEY_TYPE_ECC_ED25519 (SL_SE_KEY_TYPE_ECC_EDDSA | 0x20)
#define SL_SE_KEY_TYPE_ECC_X25519  (SL_SE_KEY_TYPE_ECC_MONTGOMERY | 0x20)

// =============================================================================
// Key storage methods
// =============================================================================

typedef uint32_t sl_se_storage_method_t;
#define SL_SE_KEY_STORAGE_EXTERNAL_PLAINTEXT 0x00
#define SL_SE_KEY_STORAGE_EXTERNAL_WRAPPED   0x01
#define SL_SE_KEY_STORAGE_INTERNAL_VOLATILE  0x02
#define SL_SE_KEY_STORAGE_INTERNAL_IMMUTABLE 0x03

// =============================================================================
// Key flags
// =============================================================================

#define SL_SE_KEY_FLAG_ASYMMETRIC_SIGNING_ONLY           (1UL << 10)
#define SL_SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PUBLIC_KEY  (1UL << 13)
#define SL_SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PRIVATE_KEY (1UL << 14)
#define SL_SE_KEY_FLAG_ALLOW_ANY_ACCESS                  (1UL << 15)
#define SL_SE_KEY_FLAG_NON_EXPORTABLE                    (1UL << 24)
#define SL_SE_KEY_FLAG_IS_DEVICE_GENERATED               (1UL << 25)

// =============================================================================
// Key slot definitions
// =============================================================================

typedef uint32_t sl_se_key_slot_t;

#define SL_SE_KEY_SLOT_VOLATILE_0                   0x00
#define SL_SE_KEY_SLOT_VOLATILE_1                   0x01
#define SL_SE_KEY_SLOT_VOLATILE_2                   0x02
#define SL_SE_KEY_SLOT_VOLATILE_3                   0x03
#define SL_SE_KEY_SLOT_APPLICATION_SECURE_DEBUG_KEY 0xF8
#define SL_SE_KEY_SLOT_APPLICATION_AES_128_KEY      0xFA
#define SL_SE_KEY_SLOT_APPLICATION_SECURE_BOOT_KEY  0xFC
#define SL_SE_KEY_SLOT_APPLICATION_ATTESTATION_KEY  0xFE
#define SL_SE_KEY_SLOT_SE_ATTESTATION_KEY           0xFF

// =============================================================================
// Wrapped key overhead
// =============================================================================

#define SLI_SE_WRAPPED_KEY_OVERHEAD (12 + 16)

// =============================================================================
// Key descriptor types (sl_se_manager_types.h compatibility)
// =============================================================================

// Buffer descriptor for key storage
typedef struct {
  uint8_t* pointer;
  uint32_t size;
} sl_se_buffer_t;

// Key storage location descriptor
typedef struct {
  sl_se_storage_method_t method;
  union {
    sl_se_buffer_t buffer;
    sl_se_key_slot_t slot;
  } location;
} sl_se_key_storage_t;

// Key descriptor
typedef struct {
  sl_se_key_type_t type;
  size_t size;
  uint32_t flags;
  sl_se_key_storage_t storage;
  uint8_t* password;
  const void* domain;
} sl_se_key_descriptor_t;

// =============================================================================
// Command context (minimal for POSIX)
// =============================================================================

typedef struct sl_se_command_context_t {
  uint32_t placeholder;
} sl_se_command_context_t;

// =============================================================================
// Cipher operation types
// =============================================================================

typedef enum { SL_SE_ENCRYPT, SL_SE_DECRYPT } sl_se_cipher_operation_t;

// =============================================================================
// Hash types
// =============================================================================

typedef enum {
  SL_SE_HASH_NONE,
  SL_SE_HASH_SHA1,
  SL_SE_HASH_SHA224,
  SL_SE_HASH_SHA256,
  SL_SE_HASH_SHA384,
  SL_SE_HASH_SHA512,
} sl_se_hash_type_t;

// =============================================================================
// Multipart hash contexts
// =============================================================================

typedef struct {
  sl_se_hash_type_t hash_type;
  uint32_t total[2];
  uint8_t state[32];
  uint8_t buffer[64];
} sl_se_sha256_multipart_context_t;

// =============================================================================
// Device key types (for attestation)
// =============================================================================

typedef enum {
  SL_SE_KEY_TYPE_IMMUTABLE_BOOT = 0,
  SL_SE_KEY_TYPE_IMMUTABLE_AUTH,
  SL_SE_KEY_TYPE_IMMUTABLE_AES_128,
  SL_SE_KEY_TYPE_IMMUTABLE_ATTESTATION,
  SL_SE_KEY_TYPE_IMMUTABLE_SE_ATTESTATION,
} sl_se_device_key_type_t;

// =============================================================================
// Certificate types
// =============================================================================

typedef uint8_t sl_se_cert_type_t;

// =============================================================================
// OTP init structure (for se_info_t)
// =============================================================================

// Tamper configuration types (mirror Gecko SDK sl_se_manager_types.h /
// sl_se_manager_defines.h for the vault-high parts used by sysinfo)
typedef uint8_t sl_se_tamper_level_t;
typedef uint8_t sl_se_tamper_filter_period_t;
typedef uint8_t sl_se_tamper_filter_threshold_t;
#define SL_SE_TAMPER_SIGNAL_NUM_SIGNALS 0x20

typedef struct {
  bool enable_secure_boot;
  bool verify_secure_boot_certificate;
  bool enable_anti_rollback;
  bool secure_boot_page_lock_narrow;
  bool secure_boot_page_lock_full;
  sl_se_tamper_level_t tamper_levels[SL_SE_TAMPER_SIGNAL_NUM_SIGNALS];
  sl_se_tamper_filter_period_t tamper_filter_period;
  sl_se_tamper_filter_threshold_t tamper_filter_threshold;
  uint8_t tamper_flags;
  uint8_t tamper_reset_threshold;
} sl_se_otp_init_t;

// =============================================================================
// SE status structure
// =============================================================================

// Debug lock options (mirrors Gecko SDK sl_se_debug_options_t)
typedef struct {
  bool non_secure_invasive_debug;
  bool non_secure_non_invasive_debug;
  bool secure_invasive_debug;
  bool secure_non_invasive_debug;
} sl_se_debug_options_t;

// Debug status (mirrors Gecko SDK sl_se_debug_status_t)
typedef struct {
  bool device_erase_enabled;
  bool secure_debug_enabled;
  bool debug_port_lock_applied;
  bool debug_port_lock_state;
  sl_se_debug_options_t options_config;
  sl_se_debug_options_t options_state;
} sl_se_debug_status_t;

typedef struct {
  uint32_t boot_status;
  uint32_t se_fw_version;
  uint32_t host_fw_version;
  sl_se_debug_status_t debug_status;
  bool secure_boot_enabled;
  uint32_t tamper_status;
  uint32_t tamper_status_raw;
} sl_se_status_t;
