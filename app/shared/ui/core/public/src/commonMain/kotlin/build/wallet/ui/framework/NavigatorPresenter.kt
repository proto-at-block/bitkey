package build.wallet.ui.framework

import androidx.compose.runtime.Composable
import build.wallet.statemachine.core.ScreenModel

/**
 * Used by [StateMachine]s to navigate to [Screen]s.
 *
 * Acts as an interoperable layer between [StateMachine]s and [ScreenPresenter]s.
 * [StateMachine]s should use [NavigatorPresenter] directly by providing an
 * initial [Screen] to navigate to.
 *
 * Internally should create and use its own [Navigator] instance to navigate to the
 * initial [Screen] and any other [Screen]s that are navigated to by the
 * [ScreenPresenter] of the initial and other [Screen]s.
 *
 * A navigating initial screen might eventually be exited back to the parent
 * state machine with some result. In this case, the state machine needs to
 * pass a result callback to the [Screen] implementation, which will be called
 * by some [ScreenPresenter] when the screen is exited. This is very similar to
 * how our current navigation system works through callbacks in props.
 *
 * Note that the callback might need to be passed through multiple screens
 * before it is called.
 *
 * This is a temporary solution while we are transitioning to fully using [Screen]s in
 * our codebase. Eventually, we will be able to navigate between screens directly
 * through [Navigator]s without using callbacks.
 *
 * Example:
 *
 * ```kotlin
 * data class SettingsScreen(
 *   val onExit: () -> Unit
 * ): Screen
 *
 * class SettingsScreenPresenter : ScreenPresenter<SettingsScreen> {
 *    @Composable
 *    override fun model(
 *      navigator: Navigator,
 *      screen: SettingsScreen,
 *    ): ScreenModel {
 *      return SettingsScreenModel(
 *        onBackClick = screen.onExit
 *      )
 *    }
 * }
 *
 * class MoneyHomeStateMachineImpl(
 *    private val navigatorPresenter: NavigatorPresenter,
 * ): StateMachine<Unit, ScreenModel> {
 *    @Composable
 *    override fun model(props: Unit): ScreenModel {
 *      var state: State by remember { mutableStateOf(ViewingAccountHomeState) }
 *
 *      return when (state) {
 *        is ViewingAccountHomeState -> ...
 *        is ViewingSettingsState ->
 *          navigatorPresenter.model(
 *            SettingsScreen(
 *              onExit = { state = ViewingAccountHomeState }
 *            )
 *          )
 *      }
 *    }
 *
 *    private sealed interface State {
 *      data object ViewingAccountHomeState : State
 *      data object ViewingSettingsState : State
 *    }
 * }
 * ```
 */
interface NavigatorPresenter {
  @Composable
  fun model(initialScreen: Screen): ScreenModel
}
