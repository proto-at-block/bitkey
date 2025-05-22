package build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints

import build.wallet.analytics.events.screen.id.EventTrackerScreenId

enum class ResetFingerprintsEventTrackerScreenId : EventTrackerScreenId {
  /** Confirmation screen for resetting fingerprints */
  CONFIRM_RESET_FINGERPRINTS,

  /** Confirmation sheet for resetting fingerprints */
  TAP_DEVICE_TO_RESET_SHEET,

  /** Progress screen showing the 7-day waiting period */
  RESET_FINGERPRINTS_PROGRESS,
}
