package build.wallet.analytics.events.screen

import build.wallet.analytics.events.EventTrackerContext
import build.wallet.analytics.events.screen.id.EventTrackerCounterId

/**
 * Information related to [ACTION_APP_COUNT] events
 */
data class EventTrackerCountInfo(
  val eventTrackerCounterId: EventTrackerCounterId,
  val eventTrackerContext: EventTrackerContext?,
  val count: Int,
) {
  constructor(
    eventTrackerCounterId: EventTrackerCounterId,
    count: Int,
  ) : this(
    eventTrackerCounterId = eventTrackerCounterId,
    eventTrackerContext = null,
    count = count
  )

  val counterId: String
    get() = when (val context = eventTrackerContext) {
      null -> eventTrackerCounterId.name
      else -> "${eventTrackerContext.name}_${context.name}"
    }
}
