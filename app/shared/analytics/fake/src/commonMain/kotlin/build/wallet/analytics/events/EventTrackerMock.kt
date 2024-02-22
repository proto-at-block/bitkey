package build.wallet.analytics.events

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.context.EventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.analytics.v1.Action
import build.wallet.analytics.v1.Action.ACTION_APP_SCREEN_IMPRESSION

class EventTrackerMock(
  turbine: (name: String) -> Turbine<TrackedAction>,
) : EventTracker {
  val eventCalls = turbine("analytics event calls")

  override fun track(action: Action) {
    eventCalls += TrackedAction(action)
  }

  override fun track(eventTrackerScreenInfo: EventTrackerScreenInfo) {
    eventCalls +=
      TrackedAction(
        ACTION_APP_SCREEN_IMPRESSION,
        eventTrackerScreenInfo.eventTrackerScreenId,
        eventTrackerScreenInfo.eventTrackerScreenIdContext
      )
  }
}

data class TrackedAction(
  val action: Action,
  val screenId: EventTrackerScreenId? = null,
  val screenIdContext: EventTrackerScreenIdContext? = null,
)
