#pragma once

#include "arithmetic.h"
#include "bip32.h"

typedef enum {
  PURPOSE_UNKNOWN,
  PURPOSE_BIP84,
  PURPOSE_W1_AUTH,
} purpose_t;

typedef enum {
  COIN_TYPE_UNKNOWN,
  COIN_TYPE_MAINNET,
  COIN_TYPE_TESTNET,
} coin_type_t;

typedef enum {
  CHANGE_UNKNOWN,
  CHANGE_EXTERNAL,
  CHANGE_INTERNAL,
} change_t;

typedef struct {
  purpose_t purpose;
  coin_type_t coin_type;
  change_t change;
  derivation_path_t path;
} derivation_path_parts_t;

#define DERIVATION_PATH(name, purpose_value, coin_type_value, change_value, indices_value) \
  static uint32_t name##_INDICES[ARRAY_SIZE(indices_value)] = indices_value;               \
  static derivation_path_parts_t name = {                                                  \
    .purpose = purpose_value,                                                              \
    .coin_type = coin_type_value,                                                          \
    .change = change_value,                                                                \
    .path =                                                                                \
      {                                                                                    \
        .indices = (uint32_t*)name##_INDICES,                                              \
        .num_indices = ARRAY_SIZE(name##_INDICES),                                         \
      },                                                                                   \
  };
