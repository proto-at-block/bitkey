package build.wallet.analytics.events.screen

import build.wallet.analytics.events.EventTrackerContext
import build.wallet.analytics.events.screen.id.EventTrackerScreenId

/**
 * Information related to [ACTION_APP_SCREEN_IMPRESSION] events, used together
 * to create a string screen ID to pass to analytics
 *
 * @property eventTrackerScreenId: Main ID for the screen, i.e. MONEY_HOME
 * @property eventTrackerContext: Optional additional context for the screen if the
 * screen is reused in different places, i.e. [ACCOUNT_CREATION] vs [HW_RECOVERY] to
 * disambiguate events when going through the pair new hardware flow.
 * @property eventTrackerShouldTrack: whether to track the event.
 * There are 2 ways to not have screen IDs be tracked by the EventTracker.
 * One is for the BodyModel to pass in null for this EventTrackerScreenInfo. However, this is not
 * always possible, because for models that are dynamic or shared across different screens,
 * we need an ID for rendering purposes (to tell us when to show a new view vs update the existing
 * one on the screen). So, for those screens, we still populate EventTrackerScreenInfo but pass
 * [false] for [eventTrackerShouldTrack]
 */
data class EventTrackerScreenInfo(
  val eventTrackerScreenId: EventTrackerScreenId,
  val eventTrackerContext: EventTrackerContext?,
  val eventTrackerShouldTrack: Boolean = true,
) {
  constructor(
    eventTrackerScreenId: EventTrackerScreenId,
    eventTrackerShouldTrack: Boolean = true,
  ) : this(
    eventTrackerScreenId = eventTrackerScreenId,
    eventTrackerContext = null,
    eventTrackerShouldTrack = eventTrackerShouldTrack
  )

  val screenId: String
    get() = when (val context = eventTrackerContext) {
      null -> eventTrackerScreenId.name
      else -> "${eventTrackerScreenId.name}_${context.name}"
    }
}
