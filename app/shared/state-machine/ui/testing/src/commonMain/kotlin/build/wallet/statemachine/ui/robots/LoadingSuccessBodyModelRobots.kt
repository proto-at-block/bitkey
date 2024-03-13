package build.wallet.statemachine.ui.robots

import app.cash.turbine.ReceiveTurbine
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.ui.awaitUntilScreenWithBody

/**
 * Awaits the next [ScreenModel] with a [LoadingSuccessBodyModel] and returns the body.
 */
suspend fun ReceiveTurbine<ScreenModel>.awaitLoadingScreen(
  id: EventTrackerScreenId?,
  state: LoadingSuccessBodyModel.State = LoadingSuccessBodyModel.State.Loading,
): LoadingSuccessBodyModel =
  awaitUntilScreenWithBody<LoadingSuccessBodyModel>(
    id, expectedBodyContentMatch = {
      it.state == state
    }
  )
