#pragma once

#include "hash.h"
#include "mcu_hash.h"

/**
 * @brief Returns the stm32 mcu type for a given hash_alg_t
 */
mcu_hash_alg_t crypto_alg_type(hash_alg_t alg);
