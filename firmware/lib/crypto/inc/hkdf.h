#pragma once

#include "hash.h"
#include "key_management.h"

bool crypto_hkdf(key_handle_t* key_in, hash_alg_t hash, uint8_t* salt, size_t salt_len,
                 uint8_t* info, size_t info_len, key_handle_t* key_out);
