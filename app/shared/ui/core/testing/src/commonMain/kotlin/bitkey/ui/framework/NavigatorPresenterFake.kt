package bitkey.ui.framework

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ScreenModel

/**
 * Fake implementation of [NavigatorPresenter] which always returns [NavigatorModelFake]
 * with [initialScreen] and [onExit] passed through [NavigatorPresenterFake.model].
 *
 * Should be used for testing interoperability between [StateMachine]s and [NavigatorPresenter].
 */
class NavigatorPresenterFake : NavigatorPresenter {
  @Composable
  override fun model(
    initialScreen: Screen,
    onExit: () -> Unit,
  ): ScreenModel {
    val model = NavigatorModelFake(initialScreen, onExit = onExit)
    return model.asRootScreen()
  }
}

data class NavigatorModelFake(
  val initialScreen: Screen,
  val onExit: () -> Unit,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null,
) : BodyModel() {
  @Composable
  override fun render(modifier: Modifier) {
    Text(initialScreen.toString())
  }
}
