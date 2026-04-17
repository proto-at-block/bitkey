#include "policy.h"

#include "attributes.h"
#include "bitlog.h"
#include "log.h"
#include "secutils.h"

#include <stdbool.h>

STATIC_VISIBLE_FOR_TESTING policy_ctx_t policy_ctx = {
  .fetch_path_cb = NULL,
  .enabled = SECURE_TRUE,
  .grant_presented = SECURE_FALSE,
};

NO_OPTIMIZE void policy_init(policy_fetch_path_cb_t fetch_path_cb, secure_bool_t enabled) {
  policy_ctx.fetch_path_cb = fetch_path_cb;
  policy_ctx.enabled = enabled;
  volatile secure_bool_t* enabled_check = &policy_ctx.enabled;
  SECURE_IF_FAILIN(*enabled_check != SECURE_TRUE) { LOGW("Policy disabled"); }
}

static bool path_matches_allowed_path(const derivation_path_t path) {
  const derivation_path_t* allowed = policy_ctx.fetch_path_cb();
  ASSERT(allowed);

  if (path.num_indices != allowed->num_indices) {
    return false;
  }

  for (uint32_t i = 0; i < path.num_indices; i++) {
    if (path.indices[i] != allowed->indices[i]) {
      return false;
    }
  }

  return true;
}

NO_OPTIMIZE policy_sign_result_t bip32_sign_with_policy(extended_key_t* key_priv,
                                                        derivation_path_t path,
                                                        uint8_t digest[SHA256_DIGEST_SIZE],
                                                        uint8_t signature_out[ECC_SIG_SIZE]) {
  ASSERT(policy_ctx.fetch_path_cb);

  volatile secure_bool_t can_sign = SECURE_FALSE;
  volatile secure_bool_t* enabled = &policy_ctx.enabled;
  volatile secure_bool_t* grant_presented = &policy_ctx.grant_presented;

  SECURE_IF_FAILOUT(*enabled == SECURE_FALSE) {
    // No policy enabled -> can sign.
    can_sign = SECURE_TRUE;
  }
  SECURE_IF_FAILOUT(*enabled == SECURE_TRUE && *grant_presented == SECURE_TRUE) {
    // Grant presented -> can sign.
    can_sign = SECURE_TRUE;
  }
  SECURE_IF_FAILOUT(*enabled == SECURE_TRUE && *grant_presented == SECURE_FALSE) {
    // No grant presented -> can sign if path matches allowed path.
    //
    // We want to prevent bitcoin transaction signing, unless a grant is presented.
    // But, we also derive the auth key off of the same seed, with a specific path. That action
    // is fine to do without a grant.
    SECURE_IF_FAILOUT(path_matches_allowed_path(path)) { can_sign = SECURE_TRUE; }
  }

  // FI-hardened check: fail-in to the denial path so a glitch cannot skip it.
  SECURE_IF_FAILIN(can_sign != SECURE_TRUE) {
    BITLOG_EVENT(wallet_policy_enforced, 0);
    LOGW("Policy violation");
    return POLICY_SIGN_POLICY_VIOLATION;
  }

  if (!bip32_sign(key_priv, digest, signature_out)) {
    LOGE("Sign fail");
    return POLICY_SIGN_SIGNING_ERROR;
  }

  return POLICY_SIGN_SUCCESS;
}

void policy_disable(void) {
  policy_ctx.enabled = SECURE_FALSE;
}

void policy_present_grant(void) {
  policy_ctx.grant_presented = SECURE_TRUE;
}
