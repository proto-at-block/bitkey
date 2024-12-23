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

  /** Reset device spendable balance check error screen */
  RESET_DEVICE_BALANCE_CHECK_ERROR,

  /** Transfer funds sheet */
  RESET_DEVICE_TRANSFER_FUNDS,

  /** Confirm to continue screen */
  RESET_DEVICE_CONFIRMATION,

  /** Scan and reset confirmation sheet */
  SCAN_AND_RESET_SHEET,

  /** Resetting device screen */
  RESET_DEVICE_IN_PROGRESS,

  /** Resetting device success screen */
  RESET_DEVICE_SUCCESS,
}
