package build.wallet.analytics.events

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.analytics.events.screen.EventTrackerCountInfo
import build.wallet.analytics.events.screen.EventTrackerFingerprintScanStatsInfo
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.EventTrackerCounterId
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.analytics.v1.Action
import build.wallet.analytics.v1.Action.ACTION_APP_SCREEN_IMPRESSION

class EventTrackerMock(
  turbine: (name: String) -> Turbine<TrackedAction>,
) : EventTracker {
  val eventCalls = turbine("analytics event calls")

  override fun track(
    action: Action,
    context: EventTrackerContext?,
  ) {
    eventCalls += TrackedAction(action = action, screenId = null, context = context)
  }

  override fun track(eventTrackerCountInfo: EventTrackerCountInfo) {
    eventCalls += TrackedAction(
      Action.ACTION_APP_COUNT,
      counterId = eventTrackerCountInfo.eventTrackerCounterId,
      count = eventTrackerCountInfo.count
    )
  }

  override fun track(eventTrackerScreenInfo: EventTrackerScreenInfo) {
    eventCalls +=
      TrackedAction(
        ACTION_APP_SCREEN_IMPRESSION,
        eventTrackerScreenInfo.eventTrackerScreenId,
        eventTrackerScreenInfo.eventTrackerContext
      )
  }

  override fun track(eventTrackerFingerprintScanStatsInfo: EventTrackerFingerprintScanStatsInfo) {
    eventCalls +=
      TrackedAction(
        Action.ACTION_HW_FINGERPRINT_SCAN_STATS
      )
  }
}

data class TrackedAction(
  val action: Action,
  val screenId: EventTrackerScreenId? = null,
  val context: EventTrackerContext? = null,
  val counterId: EventTrackerCounterId? = null,
  val count: Int? = null,
)
