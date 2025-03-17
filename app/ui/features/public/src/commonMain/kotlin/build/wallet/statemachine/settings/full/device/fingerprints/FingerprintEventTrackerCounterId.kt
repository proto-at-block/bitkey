package build.wallet.statemachine.settings.full.device.fingerprints

import build.wallet.analytics.events.screen.id.EventTrackerCounterId

enum class FingerprintEventTrackerCounterId : EventTrackerCounterId {
  /**
   * Total number of enrolled fingerprints after a new one is added.
   */
  FINGERPRINT_ADDED_COUNT,

  /**
   * Total number of enrolled fingerprints after one is deleted.
   */
  FINGERPRINT_DELETED_COUNT,
}
