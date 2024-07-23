package build.wallet.analytics.events

import build.wallet.analytics.events.screen.EventTrackerCountInfo
import build.wallet.analytics.events.screen.EventTrackerFingerprintScanStatsInfo
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.v1.Action

/**
 * Allows to record analytics events.
 */
interface EventTracker {
  /**
   * Sends analytics event
   */
  fun track(
    action: Action,
    context: EventTrackerContext? = null,
  )

  /**
   * Sends ACTION_APP_COUNT analytics event with the given screen info
   */
  fun track(eventTrackerCountInfo: EventTrackerCountInfo)

  /**
   * Sends ACTION_APP_SCREEN_IMPRESSION analytics event with the given screen info
   */
  fun track(eventTrackerScreenInfo: EventTrackerScreenInfo)

  /**
   * Sends ACTION_HW_FINGERPRINT_SCAN_STATS.
   */
  fun track(eventTrackerFingerprintScanStatsInfo: EventTrackerFingerprintScanStatsInfo)
}
