package build.wallet.statemachine.settings.full.device.wipedevice

import build.wallet.analytics.events.screen.id.EventTrackerScreenId

/**
 * NB: This feature used to be called `RESET` instead of `WIPE`. Events keep the original name
 * to retain analytics continuity.
 */
enum class WipingDeviceEventTrackerScreenId : EventTrackerScreenId {
  /** Reset device intro screen */
  RESET_DEVICE_INTRO,

  /** Scan to confirm device sheet */
  RESET_DEVICE_SCAN_SHEET,

  /** Checking whether the tapped logged-in device can be wiped */
  RESET_DEVICE_CHECKING_ELIGIBILITY,

  /** Reset device spendable balance check error screen */
  RESET_DEVICE_BALANCE_CHECK_ERROR,

  /** Transfer funds sheet */
  RESET_DEVICE_TRANSFER_FUNDS,

  /** Confirm to continue screen */
  RESET_DEVICE_CONFIRMATION,

  /** Unpaired device warning sheet */
  RESET_DEVICE_UNPAIRED_WARNING,

  /** W3 upgrade old-device wipe blocked by an active pending transaction */
  RESET_DEVICE_OLD_DEVICE_PENDING_TRANSFER,

  /** W3 upgrade old-device wipe blocked by sweepable old-device funds */
  RESET_DEVICE_OLD_DEVICE_HAS_FUNDS,

  /** W3 upgrade old-device wipe blocked by sweep transaction confirmations */
  RESET_DEVICE_OLD_DEVICE_PENDING_SWEEP_CONFIRMATION,

  /** W3 upgrade old-device wipe blocked by unknown tapped device */
  RESET_DEVICE_OLD_DEVICE_UNKNOWN,

  /** W3 upgrade old-device wipe blocked by an identity or safety-check failure */
  RESET_DEVICE_OLD_DEVICE_CHECK_FAILED,

  /** W3 upgrade old-device wipe found an already wiped or not set up device */
  RESET_DEVICE_OLD_DEVICE_ALREADY_WIPED_OR_NOT_SET_UP,

  /** Scan and reset confirmation sheet */
  SCAN_AND_RESET_SHEET,

  /** Resetting device screen */
  RESET_DEVICE_IN_PROGRESS,

  /** Resetting device success screen */
  RESET_DEVICE_SUCCESS,
}
