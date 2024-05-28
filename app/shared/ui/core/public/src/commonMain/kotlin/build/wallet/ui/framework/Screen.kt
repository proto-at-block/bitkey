package build.wallet.ui.framework

import androidx.compose.runtime.Composable
import build.wallet.statemachine.core.ScreenModel

/**
 * Represents a screen destination in the app.
 *
 * Each [Screen] implementations has to have a corresponding [ScreenPresenter],
 * which has to be registered with a [ScreenPresenterRegistry].
 *
 * The corresponding [ScreenPresenter] is responsible for producing a [ScreenModel]
 * for the [Screen].
 *
 * Other [ScreenPresenter]s can navigate to a [Screen] using [Navigator] provided
 * by the [ScreenPresenter].
 *
 * If a [StateMachine] wants to navigate to a [Screen], it should use [NavigatorPresenter]
 * instead of [Navigator] directly.
 *
 * Note that a [Screen] should not have any callbacks or logic in it.
 * The only exception is when a [ScreenPresenter] needs to interoperate with some remote
 * parent [StateMachine], where [Navigator] cannot be used since [StateMachine]s do not
 * have [Screen]. Eventually navigation between screens will be done using [Navigator],
 * without the use of callbacks.
 */
interface Screen

/**
 * A [Screen] that does not have its own [ScreenPresenter].
 *
 * This is helpful when a screen has very simple implementation and does not need
 * to inject any dependencies. A [SimpleScreen] can still have an internal state,
 * and it can use [Navigator] to navigate to other screens.
 *
 * A [SimpleScreen] does not need to be registered with a [ScreenPresenterRegistry].
 */
fun interface SimpleScreen : Screen {
  @Composable
  fun model(navigator: Navigator): ScreenModel
}
