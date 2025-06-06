#pragma once

#include "aes.h"
#include "bip32.h"
#include "ecc.h"
#include "hash.h"
#include "mempool.h"
#include "wallet.pb.h"

#include <stdbool.h>
#include <stdint.h>

typedef enum {
  WALLET_RES_OK = 0,
  WALLET_RES_ERR,  // Generic error
  WALLET_RES_ALREADY_CREATED,
  WALLET_RES_NOT_CREATED,
  WALLET_RES_WKEK_ERR,
  WALLET_RES_NO_WKEK,
  WALLET_RES_MASTER_KEY_ERR,
  WALLET_RES_STORAGE_ERR,
  WALLET_RES_KEY_DERIVATION_ERR,
  WALLET_RES_SIGNING_ERR,
  WALLET_RES_SEALING_ERR,
  WALLET_RES_UNSEALING_ERR,
  WALLET_RES_OOM,
  WALLET_RES_SERIALIZATION_ERR,
  WALLET_RES_UNIMPLEMENTED_ERR,
  WALLET_RES_NETWORK_ERR,
} wallet_res_t;

// These enum values must match with the fwpb_signing_domain proto enums
typedef enum {
  WALLET_KEY_DOMAIN_AUTH = 0,
  WALLET_KEY_DOMAIN_CONFIG = 1,
  WALLET_KEY_DOMAIN_SPEND = 2,
  WALLET_KEY_DOMAIN_MAX,
} wallet_key_domain_t;

typedef enum {
  WALLET_KEY_BUNDLE_ACTIVE,
  WALLET_KEY_BUNDLE_RECOVERY,
  WALLET_KEY_BUNDLE_INACTIVE,
} wallet_key_bundle_type_t;

typedef struct {
  uint8_t* origin_fingerprint;
  uint32_t* origin_path;
  uint8_t* serialized_bip32_key;
  uint32_t* xpub_path;
} key_descriptor_t;

#define UUID_LENGTH (36)
#define CSEK_LENGTH (AES_256_LENGTH_BYTES)

typedef struct PACKED {
  uint8_t version;
  extended_key_t bip84_external_key;
  extended_key_t bip84_internal_key;
  extended_key_t w1_auth_key;
} derived_key_cache_t;

#define DERIVED_KEY_CACHE_PATH            "derived-keys.bin"
#define DERIVED_KEY_CACHE_VERSION         (0)
#define DERIVED_KEY_CACHE_CIPHERTEXT_SIZE (sizeof(derived_key_cache_t) + AES_GCM_OVERHEAD)

#define WALLET_POOL_R0_SIZE (70)
#define WALLET_POOL_R0_NUM  (6)
#define WALLET_POOL_R1_SIZE (128)
#define WALLET_POOL_R1_NUM  (2)
#define WALLET_POOL_R2_SIZE (DERIVED_KEY_CACHE_CIPHERTEXT_SIZE)
#define WALLET_POOL_R2_NUM  (1)

_Static_assert(
  WALLET_POOL_R2_SIZE >= DERIVED_KEY_CACHE_CIPHERTEXT_SIZE,
  "There must a pool large enough to fully serialize an encrypted derived_key_cache_t");

#define BITCOIN fwpb_btc_network_BITCOIN
#define TESTNET fwpb_btc_network_TESTNET
#define SIGNET  fwpb_btc_network_SIGNET
#define REGTEST fwpb_btc_network_REGTEST

void wallet_init(mempool_t* mempool);

bool wallet_is_initialized(void);

void wallet_clear_derived_key_cache(void);
bool wallet_derive_key_priv_using_cache(extended_key_t* key_priv,
                                        derivation_path_t derivation_path);

wallet_res_t wallet_csek_encrypt(uint8_t* unwrapped_csek, uint8_t* wrapped_csek_out,
                                 uint32_t length, uint8_t iv_out[AES_GCM_IV_LENGTH],
                                 uint8_t tag_out[AES_GCM_TAG_LENGTH]);
wallet_res_t wallet_csek_decrypt(uint8_t* wrapped_csek, uint8_t* unwrapped_csek_out,
                                 uint32_t length, uint8_t iv[AES_GCM_IV_LENGTH],
                                 uint8_t tag[AES_GCM_TAG_LENGTH]);

derivation_path_t* wallet_get_w1_auth_path(void);
bool wallet_get_w1_auth_key(extended_key_t* key_priv);

// Store data in flash, wrapped by the WKEK.
bool wkek_encrypt_and_store(char* filename, const uint8_t* data, uint32_t size);
// Read data from flash, decrypt with WKEK. `data_out` is memory managed by the caller
// and must be at least `size` bytes.
bool wkek_read_and_decrypt(char* filename, uint8_t* data_out, uint32_t size);

// From wallet_storage.c
void wallet_remove_files(void);  // Delete all wallet state.
