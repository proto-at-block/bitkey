#include "psbt.h"

#include "ew.h"
#include "secure_rng.h"

bool psbt_lib_is_ready(void) {
  ew_api_t api = {
    .crypto_random = NULL,
    .secure_memzero = NULL,
    .malloc = NULL,
    .free = NULL,
    .ecdsa_sign = NULL,
    .ecdsa_verify = NULL,
  };

  return ew_init(&api) == EW_OK;
}
