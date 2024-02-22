#pragma once

#include "ecc.h"
#include "hash.h"

#include <stdbool.h>
#include <stdint.h>

// BIP32 library with no dynamic memory allocation.
// https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki

#define BIP32_MAX_DERIVATION_DEPTH                (256u)
#define BIP32_KEY_SIZE                            (32u)
#define BIP32_SEC1_KEY_SIZE                       (BIP32_KEY_SIZE + 1)
#define BIP32_CHAINCODE_SIZE                      (32u)
#define BIP32_SERIALIZED_EXT_KEY_SIZE             (78u)
#define BIP32_SERIALIZED_B58_ENCODED_EXT_KEY_SIZE (112u)
#define BIP32_HARDENED_BIT                        (0x80000000)

#define BIP32_PRIVKEY_PREFIX        (0x00)
#define SEC1_COMPRESSED_PUBKEY_EVEN (0x02)
#define SEC1_COMPRESSED_PUBKEY_ODD  (0x03)

#define BIP32_KEY_FINGERPRINT_SIZE (4)
typedef struct {
  uint8_t bytes[BIP32_KEY_FINGERPRINT_SIZE];
} fingerprint_t;

typedef struct {
  uint32_t* indices;
  uint32_t num_indices;
} derivation_path_t;

typedef struct {
  uint8_t prefix;
  uint8_t key[BIP32_KEY_SIZE];
  uint8_t chaincode[BIP32_CHAINCODE_SIZE];
} __attribute__((__packed__)) extended_key_t;

typedef enum {
  MAINNET_PUB = 0x0488B21E,
  MAINNET_PRIV = 0x0488ADE4,
  TESTNET_PUB = 0x043587CF,
  TESTNET_PRIV = 0x04358394,
} version_bytes_t;

bool bip32_derive_master_key(const uint8_t* seed, uint32_t seed_length,
                             extended_key_t* master_key_out);

bool bip32_priv_to_pub(extended_key_t* priv_in, extended_key_t* pub_out);

bool bip32_derive_path_priv(extended_key_t* priv_parent, extended_key_t* priv_child_out,
                            uint8_t childs_parent_fingerprint_out[BIP32_KEY_FINGERPRINT_SIZE],
                            const derivation_path_t* path);

bool bip32_derive_path_pub(extended_key_t* pub_parent, extended_key_t* pub_child_out,
                           derivation_path_t* path);

bool bip32_compute_fingerprint(extended_key_t* pub_key,
                               uint8_t fingerprint[BIP32_KEY_FINGERPRINT_SIZE]);

bool bip32_fingerprint_for_path(extended_key_t* priv_key, derivation_path_t* path,
                                uint8_t fingerprint[BIP32_KEY_FINGERPRINT_SIZE]);

// Serialize `ext_key`.
// One of `parent_pub_key` or `parent_fingerprint` must be set. If both are set,
bool bip32_serialize_ext_key(extended_key_t* ext_key, extended_key_t* parent_pub_key,
                             uint8_t parent_fingerprint[BIP32_KEY_FINGERPRINT_SIZE],
                             version_bytes_t version, uint32_t child_num, uint8_t depth,
                             uint8_t* serialized, uint32_t serialized_size);

bool bip32_sign(extended_key_t* priv_key, uint8_t digest[SHA256_DIGEST_SIZE],
                uint8_t signature_out[ECC_SIG_SIZE]);

void bip32_zero_key(extended_key_t* const key);
