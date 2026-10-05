#pragma once

#include "bip32.h"
#include "wstring.h"

#include <stdbool.h>
#include <stdint.h>

static inline bool crypto_task_indices_match(const derivation_path_t* staged_path,
                                             const uint32_t* expected_indices,
                                             uint32_t num_indices) {
  if (num_indices > BIP32_MAX_DERIVATION_DEPTH || num_indices != staged_path->num_indices) {
    return false;
  }

  return memcmp_s(staged_path->indices, expected_indices,
                  num_indices * sizeof(*expected_indices)) == 0;
}
