package build.wallet.analytics.events.count.id

import build.wallet.analytics.events.screen.id.EventTrackerCounterId

enum class SocialRecoveryEventTrackerCounterId : EventTrackerCounterId {
  /**
   * Total number of Recovery Contacts the Protected Customer has at the time of the event.
   */
  SOCREC_COUNT_TOTAL_TCS,
}
