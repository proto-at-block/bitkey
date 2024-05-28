package build.wallet.ui.framework

import androidx.compose.runtime.Composable
import build.wallet.statemachine.core.ScreenModel

/**
 * Responsible for managing presentation logic and producing [ScreenModel]s
 * for the corresponding [ScreenT].
 *
 * A [ScreenPresenter] can use the [Navigator] to navigate to other [Screen]s imperatively.
 *
 * The [ScreenPresenter] has to be registered with a [ScreenPresenterRegistry],
 * in order for the [NavigatorPresenter] to be able to produce the right [ScreenModel] for a
 * given [Screen]. Not doing so will result in a runtime error.
 */
fun interface ScreenPresenter<ScreenT : Screen> {
  @Composable
  fun model(
    navigator: Navigator,
    screen: ScreenT,
  ): ScreenModel
}
