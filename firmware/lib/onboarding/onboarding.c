#include "onboarding.h"

#include "assert.h"
#include "bio_storage.h"
#include "grant_protocol.h"
#include "kv.h"
#include "log.h"
#include "unlock.h"
#include "wallet.h"

secure_bool_t onboarding_complete(void) {
  bool biometrics_auth_setup = bio_fingerprint_exists();

  bool unlock_secret_setup = false;
  if (unlock_secret_exists(&unlock_secret_setup) != UNLOCK_OK) {
    unlock_secret_setup = false;  // As a precaution, default to false.
  }

  bool wallet_initialized = wallet_is_initialized();

  if (biometrics_auth_setup || unlock_secret_setup || wallet_initialized) {
    return SECURE_TRUE;
  } else {
    return SECURE_FALSE;
  }
}

void onboarding_wipe_state(void) {
  LOGW("Wiping state!");
  wallet_remove_files();
  bio_wipe_state();
  unlock_wipe_state();
  kv_wipe_state();
  grant_protocol_delete_app_auth_pubkey();
  grant_protocol_delete_outstanding_request();
}
