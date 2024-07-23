package build.wallet.statemachine.core

import build.wallet.analytics.events.EventTrackerContext
import build.wallet.analytics.events.screen.id.EventTrackerScreenId

/**
 * A screen with a horizontally left-aligned, vertically top-aligned animated loading icon.
 *
 * Note: This screen will seamlessly animate into a success screen if the success screen is
 * also built using [LoadingSuccessBodyModel].
 *
 * @property id: A unique identifier for this screen that will also be used to track screen
 * analytic events.
 * @property message to display while showing loading screen.
 * @property onBack callback for back navigation. If `null`, effectively disables navigation back.
 */
fun LoadingBodyModel(
  id: EventTrackerScreenId?,
  onBack: (() -> Unit)? = null,
  message: String? = null,
  eventTrackerContext: EventTrackerContext? = null,
  eventTrackerShouldTrack: Boolean = true,
): LoadingSuccessBodyModel {
  return LoadingSuccessBodyModel(
    onBack = onBack,
    id = id,
    message = message,
    state = LoadingSuccessBodyModel.State.Loading,
    eventTrackerContext = eventTrackerContext,
    eventTrackerShouldTrack = eventTrackerShouldTrack
  )
}
