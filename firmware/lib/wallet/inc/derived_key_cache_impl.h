#pragma once

#include "arithmetic.h"
#include "bip32.h"

#include <stdint.h>

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

DERIVATION_PATH(BIP84_MAINNET_EXTERNAL, PURPOSE_BIP84, COIN_TYPE_MAINNET, CHANGE_EXTERNAL,
                ((uint32_t[4]){
                  84 | BIP32_HARDENED_BIT,
                  0 | BIP32_HARDENED_BIT,
                  0 | BIP32_HARDENED_BIT,
                  0,
                }));

DERIVATION_PATH(BIP84_MAINNET_INTERNAL, PURPOSE_BIP84, COIN_TYPE_MAINNET, CHANGE_INTERNAL,
                ((uint32_t[4]){
                  84 | BIP32_HARDENED_BIT,
                  0 | BIP32_HARDENED_BIT,
                  0 | BIP32_HARDENED_BIT,
                  1,
                }));

DERIVATION_PATH(BIP84_TESTNET_EXTERNAL, PURPOSE_BIP84, COIN_TYPE_TESTNET, CHANGE_EXTERNAL,
                ((uint32_t[4]){
                  84 | BIP32_HARDENED_BIT,
                  1 | BIP32_HARDENED_BIT,
                  0 | BIP32_HARDENED_BIT,
                  0,
                }));

DERIVATION_PATH(BIP84_TESTNET_INTERNAL, PURPOSE_BIP84, COIN_TYPE_TESTNET, CHANGE_INTERNAL,
                ((uint32_t[4]){
                  84 | BIP32_HARDENED_BIT,
                  1 | BIP32_HARDENED_BIT,
                  0 | BIP32_HARDENED_BIT,
                  1,
                }));

DERIVATION_PATH(W1_AUTH, PURPOSE_W1_AUTH, COIN_TYPE_UNKNOWN, CHANGE_UNKNOWN,
                ((uint32_t[2]){
                  87497287 | BIP32_HARDENED_BIT,
                  0 | BIP32_HARDENED_BIT,
                }));

// Ordered by most likely to be used in descending order.
static const derivation_path_parts_t* DERIVATION_PATHS[] = {
  &BIP84_MAINNET_EXTERNAL, &BIP84_MAINNET_INTERNAL, &W1_AUTH,
  &BIP84_TESTNET_EXTERNAL, &BIP84_TESTNET_INTERNAL,
};
