package build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints

import build.wallet.analytics.events.screen.id.EventTrackerScreenId

enum class ResetFingerprintsEventTrackerScreenId : EventTrackerScreenId {
  /** Confirmation screen for resetting fingerprints */
  CONFIRM_RESET_FINGERPRINTS,

  /** Confirmation sheet for resetting fingerprints */
  TAP_DEVICE_TO_RESET_SHEET,

  /** Progress screen showing the 7-day waiting period */
  RESET_FINGERPRINTS_PROGRESS,

  /** Loading screen when checking fingerprint reset status */
  LOADING_FINGERPRINT_RESET_STATUS,

  /** Loading screen when cancelling the fingerprint reset */
  CANCEL_FINGERPRINT_RESET_LOADING,

  /** Error shown when starting the reset process fails. */
  ERROR_STARTING_RESET,

  /** Error shown when finalizing the reset process fails. */
  ERROR_FINALIZING_RESET,

  /** Error shown when cancelling the reset process fails. */
  ERROR_CANCELLING_RESET,

  /** Error shown when an NFC operation fails unexpectedly. */
  ERROR_NFC_OPERATION_FAILED,
}
