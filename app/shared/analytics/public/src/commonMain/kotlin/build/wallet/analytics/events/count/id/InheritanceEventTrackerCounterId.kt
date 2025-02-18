package build.wallet.analytics.events.count.id

import build.wallet.analytics.events.screen.id.EventTrackerCounterId

enum class InheritanceEventTrackerCounterId : EventTrackerCounterId {
  /**
   * Total number of beneficiaries the benefactor has at the time of the event.
   */
  INHERITANCE_COUNT_TOTAL_BENEFICIARIES,
}
