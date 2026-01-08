#include "crypto_stm32_common.h"

#include "assert.h"
#include "hash.h"
#include "mcu_hash.h"

mcu_hash_alg_t crypto_alg_type(hash_alg_t alg) {
  switch (alg) {
    case ALG_SHA1:
      return MCU_HASH_ALG_SHA1;

    case ALG_SHA256:
      return MCU_HASH_ALG_SHA256;

    case ALG_MD5:
      return MCU_HASH_ALG_MD5;

    default:
      /* Not supported. */
      ASSERT(false);
      return MCU_HASH_ALG_NONE;
  }
}
