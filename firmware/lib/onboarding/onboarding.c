#include "onboarding.h"

#include "assert.h"
#include "bio_storage.h"
#include "grant_protocol.h"
#include "kv.h"
#include "log.h"
#include "unlock.h"
#include "wallet.h"
#include "wkek.h"

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

// Check if any onboarding-related file still exists after a wipe attempt.
// This covers the files checked by onboarding_complete() plus WKEK,
// which can be left behind if a reset interrupts wallet_remove_files().
static bool onboarding_files_remain(void) {
  if (wallet_is_initialized())
    return true;

  if (wkek_exists())
    return true;

  bool unlock_exists = false;
  if (unlock_secret_exists(&unlock_exists) == UNLOCK_OK && unlock_exists)
    return true;

  if (bio_fingerprint_exists())
    return true;

  return false;
}

void onboarding_wipe_state(void) {
  LOGW("Wipe state");

  // Delete in order of least → most critical for authentication.
  // If a reset interrupts this sequence, the auth-critical files
  // (fingerprints) are the most likely to survive, keeping the device
  // unlockable so the user can retry the wipe.
  grant_protocol_delete_outstanding_request();
  grant_protocol_delete_app_auth_pubkey();
  kv_wipe_state();
  wallet_remove_files();
  unlock_wipe_state();
  bio_wipe_state();  // Primary auth method — deleted last

  // Verify all onboarding files are gone.  If any survived (e.g. a
  // reset interrupted a previous wipe attempt and LittleFS rolled back
  // the metadata), retry all subsystem wipes.
  for (int attempt = 0; attempt < 3 && onboarding_files_remain(); attempt++) {
    LOGW("Wipe verify retry %d", attempt + 1);
    grant_protocol_delete_outstanding_request();
    grant_protocol_delete_app_auth_pubkey();
    kv_wipe_state();
    wallet_remove_files();
    unlock_wipe_state();
    bio_wipe_state();
  }
}
