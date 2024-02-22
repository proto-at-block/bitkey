#include "hash.h"

#include <stdbool.h>
#include <stdint.h>

bool mbedtls_ripemd160(uint8_t* data, size_t len, uint8_t digest[HASH160_DIGEST_SIZE]);
