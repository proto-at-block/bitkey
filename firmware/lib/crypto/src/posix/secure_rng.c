#include "secure_rng.h"

#include "crypto_impl.h"

#include <openssl/rand.h>

bool crypto_random(uint8_t* data, uint32_t num_bytes) {
  return (RAND_bytes(data, num_bytes) == OPENSSL_OK);
}
