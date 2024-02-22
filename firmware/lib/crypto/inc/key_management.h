#pragma once

#include <stdbool.h>
#include <stdint.h>

// TODO These defines need to move, since they are implementation-dependent.
#define SE_WRAPPED_KEY_OVERHEAD                       (12 + 16)
#define SE_KEY_FLAG_NON_EXPORTABLE                    (1UL << 24)
#define SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PUBLIC_KEY  (1UL << 13)
#define SE_KEY_FLAG_ASYMMETRIC_BUFFER_HAS_PRIVATE_KEY (1UL << 14)
#define SE_KEY_FLAG_ASYMMETRIC_SIGNING_ONLY           (1UL << 10)
#define SE_KEY_TYPE_SYMMETRIC                         0x00000000

#define SE_SECURE_BOOT_PUBKEY_SLOT (0xFC)

typedef enum {
  ALG_AES_128 = 0,
  ALG_AES_256 = 1,
  ALG_HMAC = 2,
  ALG_ECC_P256 = 3,
  ALG_ECC_SECP256K1 = 4,
  ALG_ECC_ED25519 = 5,
  ALG_KEY_DERIVATION = 6,
} key_algorithm_t;

typedef enum {
  KEY_STORAGE_EXTERNAL_PLAINTEXT = 0,  // Key is available in plaintext to firmware
  KEY_STORAGE_EXTERNAL_WRAPPED =
    1,  // Key is encrypted by a secure engine, and not exposed to firmware
  KEY_STORAGE_INTERNAL_IMMUTABLE = 2,  // Key is stored inside a secure engine
  KEY_STORAGE_INTERNAL_VOLATILE = 3,   // Key is ephemeral inside a secure engine
} key_storage_type_t;

typedef uint32_t key_acl_t;
typedef uint32_t key_slot_t;

typedef struct {
  uint8_t* bytes;  // May be plaintext or encrypted (wrapped)
  uint32_t size;   // In bytes
} key_buffer_t;

typedef struct {
  key_algorithm_t alg;
  uint32_t acl;  // Access control list: usage restrictions, permissions, attributes, etc.
  key_storage_type_t storage_type;
  union {
    key_slot_t slot;   // For internally stored keys
    key_buffer_t key;  // For externally stored keys
  };
} key_handle_t;

bool generate_key(key_handle_t* key);
bool import_key(key_handle_t* key_in, key_handle_t* key_out);
bool export_key(key_handle_t* key_in, key_handle_t* key_out);
bool export_pubkey(key_handle_t* key_in, key_handle_t* key_out);
void zeroize_key(key_handle_t* const key);
