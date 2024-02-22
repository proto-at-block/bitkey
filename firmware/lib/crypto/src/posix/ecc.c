#include "ecc.h"

secure_bool_t crypto_ecc_verify_hash(key_handle_t* key, uint8_t* hash, uint32_t hash_size,
                                     uint8_t signature[ECC_SIG_SIZE]) {
  // Just a stub for now.
  return SECURE_TRUE;
}
