package build.wallet.analytics.events

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.v1.Action

/**
 * Allows to record analytics events.
 */
interface EventTracker {
  /**
   * Sends analytics event
   */
  fun track(action: Action)

  /**
   * Sends ACTION_APP_SCREEN_IMPRESSION analytics event with the given screen info
   */
  fun track(eventTrackerScreenInfo: EventTrackerScreenInfo)
}
