#include "crypto_task_utils.h"

#include <criterion/criterion.h>

#include <stdint.h>

Test(crypto_task, accepts_matching_path_at_maximum_depth) {
  uint32_t staged_indices[BIP32_MAX_DERIVATION_DEPTH] = {0};
  uint32_t expected_indices[BIP32_MAX_DERIVATION_DEPTH] = {0};
  derivation_path_t staged_path = {
    .indices = staged_indices,
    .num_indices = BIP32_MAX_DERIVATION_DEPTH,
  };

  cr_assert(crypto_task_indices_match(&staged_path, expected_indices, BIP32_MAX_DERIVATION_DEPTH));
}

Test(crypto_task, rejects_path_beyond_maximum_depth_before_comparison) {
  struct {
    uint32_t indices[BIP32_MAX_DERIVATION_DEPTH];
    uint32_t adjacent;
  } staged = {
    .indices = {0},
    .adjacent = 0xa5a5a5a5,
  };
  uint32_t expected_indices[BIP32_MAX_DERIVATION_DEPTH + 1] = {0};
  expected_indices[BIP32_MAX_DERIVATION_DEPTH] = staged.adjacent;
  derivation_path_t staged_path = {
    .indices = staged.indices,
    .num_indices = BIP32_MAX_DERIVATION_DEPTH,
  };

  cr_assert_not(
    crypto_task_indices_match(&staged_path, expected_indices, BIP32_MAX_DERIVATION_DEPTH + 1));
}

Test(crypto_task, rejects_count_that_would_overflow_byte_length) {
  uint32_t staged_indices[1] = {0};
  uint32_t expected_indices[1] = {0};
  derivation_path_t staged_path = {
    .indices = staged_indices,
    .num_indices = 1,
  };

  cr_assert_not(crypto_task_indices_match(&staged_path, expected_indices, UINT32_MAX));
}

Test(crypto_task, rejects_matching_prefix_with_different_count) {
  uint32_t staged_indices[2] = {1, 2};
  uint32_t expected_indices[1] = {1};
  derivation_path_t staged_path = {
    .indices = staged_indices,
    .num_indices = 2,
  };

  cr_assert_not(crypto_task_indices_match(&staged_path, expected_indices, 1));
}
