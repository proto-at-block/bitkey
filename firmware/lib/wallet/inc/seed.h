#pragma once

#include "bip32.h"
#include "key_management.h"

typedef enum {
  SEED_RES_OK = 0,
  SEED_RES_ERR_WKEK,                // Unable to initialize the wallet KEK
  SEED_RES_ERR_SEED_WRITE,          // Unable to write the seed to the filesystem
  SEED_RES_ERR_MASTER_DERIVE,       // Unable to derive the master key
  SEED_RES_ERR_MASTER_FINGERPRINT,  // Unable to compute the master key fingerprint
  SEED_RES_ERR_DERIVE_CHILD,        // Unable to derive the child key
} seed_res_t;

#define SEED_DERIVE_HKDF_PRIVKEY_SIZE (32)

seed_res_t seed_derive_bip32(const derivation_path_t path, extended_key_t* key,
                             fingerprint_t* master_fingerprint,
                             fingerprint_t* childs_parent_fingerprint);

seed_res_t seed_derive_hkdf(key_algorithm_t algorithm, uint8_t* label, size_t label_len,
                            key_handle_t* privkey_out, key_handle_t* pubkey_out);
