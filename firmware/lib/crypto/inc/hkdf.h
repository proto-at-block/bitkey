#pragma once

#include "hash.h"
#include "key_management.h"

// We need a max here so we know how much space to allocate in the implementation.
// 32 bytes should be enough for current use cases.
#define CRYPTO_HKDF_INFO_MAX_LEN 32

bool crypto_hkdf(key_handle_t* key_in, hash_alg_t hash, uint8_t const* salt, size_t salt_len,
                 uint8_t const* info, size_t info_len, key_handle_t* key_out);
