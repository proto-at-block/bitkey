package build.wallet.ui.framework

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simple implementation of [Navigator] backed by a [MutableStateFlow] to
 * track the current screen.
 */
internal class NavigatorImpl(
  initialScreen: Screen,
) : Navigator {
  private val screenState = MutableStateFlow(initialScreen)
  val currentScreen: StateFlow<Screen> = screenState.asStateFlow()

  override fun goTo(screen: Screen) {
    screenState.value = screen
  }
}
