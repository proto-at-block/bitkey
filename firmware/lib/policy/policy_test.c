#include "bip32.h"
#include "bitlog.h"
#include "criterion_test_utils.h"
#include "fff.h"
#include "policy.h"
#include "rtos.h"
#include "secure_rng.h"
#include "secutils.h"

#include <criterion/criterion.h>

DEFINE_FFF_GLOBALS;

FAKE_VOID_FUNC(rtos_mutex_create, rtos_mutex_t*);
FAKE_VALUE_FUNC(bool, rtos_mutex_lock, rtos_mutex_t*);
FAKE_VALUE_FUNC(bool, rtos_mutex_unlock, rtos_mutex_t*);

extern uint8_t bitlog_storage[];
extern policy_ctx_t policy_ctx;

// Matches authentication.rs in app/rust:
//    Purpose: "W1HW" => [87, 49, 72, 87]
//    ChildNumber::Hardened { index: 87497287 },
//    Auth key index: 0
//    ChildNumber::Hardened { index: 0 },
static derivation_path_t w1_auth_path = {
  .indices =
    (uint32_t[2]){
      87497287 | BIP32_HARDENED_BIT,
      0 | BIP32_HARDENED_BIT,
    },
  .num_indices = 2,
};

derivation_path_t* get_w1_auth_path(void) {
  return &w1_auth_path;
}

void detect_glitch(void) {}
uint32_t clock_get_freq(void) {
  return 1;
}
uint32_t timestamp(void) {
  return 0;
}

static void init(void) {
  secutils_init((secutils_api_t){
    .detect_glitch = &detect_glitch,
    .secure_random = &crypto_rand_short,
    .cpu_freq = &clock_get_freq,
  });

  crypto_ecc_secp256k1_init();

  bitlog_init((bitlog_api_t){
    .timestamp_cb = timestamp,
  });
}

Test(policy_test, policy_disabled, .init = init) {
  policy_init(get_w1_auth_path, false);

  extended_key_t key_priv = {0};
  uint8_t digest[SHA256_DIGEST_SIZE] = {0};
  uint8_t signature[ECC_SIG_SIZE] = {0};

  // Set up some valid key.
  uint8_t seed[32] = {0};
  cr_assert(bip32_derive_master_key(seed, sizeof(seed), &key_priv));

  derivation_path_t any_path = {
    .indices = (uint32_t[1]){123},
    .num_indices = 1,
  };

  policy_sign_result_t result = bip32_sign_with_policy(&key_priv, any_path, digest, signature);
  cr_assert_eq(result, POLICY_SIGN_SUCCESS, "Signing should succeed when policy is disabled.");
}

Test(policy_test, policy_enabled_no_grant, .init = init) {
  policy_init(get_w1_auth_path, true);

  extended_key_t key_priv = {0};
  uint8_t digest[SHA256_DIGEST_SIZE] = {0};
  uint8_t signature[ECC_SIG_SIZE] = {0};

  // Set up some valid key.
  uint8_t seed[32] = {0};
  cr_assert(bip32_derive_master_key(seed, sizeof(seed), &key_priv));

  derivation_path_t any_path = {
    .indices = (uint32_t[1]){123},
    .num_indices = 1,
  };

  policy_sign_result_t result = bip32_sign_with_policy(&key_priv, any_path, digest, signature);
  cr_assert_eq(result, POLICY_SIGN_POLICY_VIOLATION, "Policy violation should fail.");
}

Test(policy_test, policy_enabled_yes_grant, .init = init) {
  policy_init(get_w1_auth_path, true);

  extended_key_t key_priv = {0};
  uint8_t digest[SHA256_DIGEST_SIZE] = {0};
  uint8_t signature[ECC_SIG_SIZE] = {0};

  // Set up some valid key.
  uint8_t seed[32] = {0};
  cr_assert(bip32_derive_master_key(seed, sizeof(seed), &key_priv));

  derivation_path_t any_path = {
    .indices = (uint32_t[1]){123},
    .num_indices = 1,
  };

  policy_ctx.grant_presented = true;

  policy_sign_result_t result = bip32_sign_with_policy(&key_priv, any_path, digest, signature);
  cr_assert_eq(result, POLICY_SIGN_SUCCESS, "Signing should succeed when policy is enabled.");
}

Test(policy_test, policy_enabled_auth_key, .init = init) {
  policy_init(get_w1_auth_path, true);

  extended_key_t key_priv = {0};
  uint8_t digest[SHA256_DIGEST_SIZE] = {0};
  uint8_t signature[ECC_SIG_SIZE] = {0};

  // Set up some valid key.
  uint8_t seed[32] = {0};
  cr_assert(bip32_derive_master_key(seed, sizeof(seed), &key_priv));

  derivation_path_t auth_path = {
    .indices =
      (uint32_t[2]){
        87497287 | BIP32_HARDENED_BIT,
        0 | BIP32_HARDENED_BIT,
      },
    .num_indices = 2,
  };

  policy_sign_result_t result = bip32_sign_with_policy(&key_priv, auth_path, digest, signature);
  cr_assert_eq(result, POLICY_SIGN_SUCCESS, "Signing should succeed for auth path.");
}
