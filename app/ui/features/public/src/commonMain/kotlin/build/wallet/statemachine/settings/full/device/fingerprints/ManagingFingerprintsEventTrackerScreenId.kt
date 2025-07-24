package build.wallet.statemachine.settings.full.device.fingerprints

import build.wallet.analytics.events.screen.id.EventTrackerScreenId

enum class ManagingFingerprintsEventTrackerScreenId : EventTrackerScreenId {
  /** Listing enrolled fingerprints */
  LIST_FINGERPRINTS,

  /** Editing a fingerprint handle */
  EDIT_FINGERPRINT,

  /** Fingerprint deletion confirmation */
  CONFIRM_DELETE_FINGERPRINT,

  /** Enrolling a backup fingerprint */
  ENROLLING_NEW_FINGERPRINT,

  /** Onboarding screen for adding an additional fingerprint. */
  ADD_ADDITIONAL_FINGERPRINT_EXPLAINER,

  /** Loading enrolled fingerprints */
  LOADING_ENROLLED_FINGERPRINTS,
}
