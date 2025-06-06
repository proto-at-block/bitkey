#pragma once

#include "bip32.h"

#include <stdint.h>

typedef enum {
  POLICY_SIGN_SUCCESS,
  POLICY_SIGN_POLICY_VIOLATION,
  POLICY_SIGN_SIGNING_ERROR,
} policy_sign_result_t;

typedef derivation_path_t* (*policy_fetch_path_cb_t)(void);

typedef struct {
  policy_fetch_path_cb_t fetch_path_cb;
  // Are we enforcing policies?
  bool enabled;
  // Has the server presented a grant which allows us to sign
  // bitcoin transactions?
  bool grant_presented;
} policy_ctx_t;

// Initialize the policy module with the provided callback function.
// Necessary to break a potential circular dependency between bip32, wallet, policy, and key cache.
void policy_init(policy_fetch_path_cb_t fetch_path_cb, bool enabled);

// Enforce signing policies.
// CRITICAL:
//   The private key *MUST MATCH* the one derived from the path.
//   It is the RESPONSIBILITY OF THE CALLER to ensure this.
//   The path is used for enforcing policy, NOT the private key.
policy_sign_result_t bip32_sign_with_policy(extended_key_t* key_priv, derivation_path_t path,
                                            uint8_t digest[SHA256_DIGEST_SIZE],
                                            uint8_t signature_out[ECC_SIG_SIZE]);

void policy_present_grant(void);

void policy_disable(void);
