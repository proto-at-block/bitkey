#include "policy.h"

#include "attributes.h"
#include "bitlog.h"
#include "log.h"

#include <stdbool.h>

STATIC_VISIBLE_FOR_TESTING policy_ctx_t policy_ctx = {
  .fetch_path_cb = NULL,
  .enabled = true,
  .grant_presented = false,
};

void policy_init(policy_fetch_path_cb_t fetch_path_cb, bool enabled) {
  policy_ctx.fetch_path_cb = fetch_path_cb;
  policy_ctx.enabled = enabled;
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

policy_sign_result_t bip32_sign_with_policy(extended_key_t* key_priv, derivation_path_t path,
                                            uint8_t digest[SHA256_DIGEST_SIZE],
                                            uint8_t signature_out[ECC_SIG_SIZE]) {
  ASSERT(policy_ctx.fetch_path_cb);

  bool can_sign = false;

  if (!policy_ctx.enabled) {
    // No policy enabled -> can sign.
    can_sign = true;
  }
  if (policy_ctx.enabled && policy_ctx.grant_presented) {
    // Grant presented -> can sign.
    can_sign = true;
  }
  if (policy_ctx.enabled && !policy_ctx.grant_presented) {
    // No grant presented -> can sign if path matches allowed path.
    //
    // We want to prevent bitcoin transaction signing, unless a grant is presented.
    // But, we also derive the auth key off of the same seed, with a specific path. That action
    // is fine to do without a grant.
    can_sign = path_matches_allowed_path(path);
  }

  if (!can_sign) {
    BITLOG_EVENT(wallet_policy_enforced, 0);
    LOGW("Signing policy violation");
    return POLICY_SIGN_POLICY_VIOLATION;
  }

  if (!bip32_sign(key_priv, digest, signature_out)) {
    LOGE("Signing error");
    return POLICY_SIGN_SIGNING_ERROR;
  }

  return POLICY_SIGN_SUCCESS;
}

void policy_disable(void) {
  policy_ctx.enabled = false;
}

void policy_present_grant(void) {
  policy_ctx.grant_presented = true;
}
