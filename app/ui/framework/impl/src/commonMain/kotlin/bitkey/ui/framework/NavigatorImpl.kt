package bitkey.ui.framework

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simple implementation of [Navigator] backed by a [MutableStateFlow] to
 * track the current screen.
 *
 * @param onExit callback executed when some screen calls [Navigator.exit].
 */
internal class NavigatorImpl(
  initialScreen: Screen,
  private val onExit: () -> Unit,
) : Navigator {
  private val screenState = MutableStateFlow(initialScreen)

  internal val currentScreen: StateFlow<Screen> = screenState.asStateFlow()

  override fun goTo(screen: Screen) {
    screenState.value = screen
  }

  override fun exit() = onExit()
}
