package bitkey.ui.framework

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
  private val screenState = MutableStateFlow(ScreenState(screen = initialScreen, sheet = null))

  internal val currentScreenState: StateFlow<ScreenState> = screenState.asStateFlow()

  override fun goTo(screen: Screen) {
    // if the screen is the same, do nothing
    // otherwise update the screen and clear the sheet
    if (screenState.value.screen == screen) {
      return
    } else {
      screenState.update {
        it.copy(screen = screen, sheet = null)
      }
    }
  }

  override fun showSheet(sheet: Sheet) {
    screenState.update {
      it.copy(sheet = sheet)
    }
  }

  override fun closeSheet() {
    screenState.update {
      it.copy(sheet = null)
    }
  }

  override fun exit() = onExit()
}

/**
 * Data class representing the current screen and the sheet.
 *
 * @param screen The current screen.
 * @param sheet The optional sheet displayed on top of the current screen.
 */
internal data class ScreenState(
  val screen: Screen,
  val sheet: Sheet? = null,
)
