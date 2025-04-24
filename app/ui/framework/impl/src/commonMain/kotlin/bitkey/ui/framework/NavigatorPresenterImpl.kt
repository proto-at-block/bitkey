package bitkey.ui.framework

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.ScreenModel

/**
 * Implementation of [NavigatorPresenter] that uses a [ScreenPresenterRegistry]
 * to fetch the [ScreenPresenter] for a given [Screen].
 *
 * Also creates and uses its own [NavigatorImpl] instance to track which
 * [Screen] is currently being shown.
 */
@BitkeyInject(ActivityScope::class)
class NavigatorPresenterImpl(
  private val screenPresenterRegistry: ScreenPresenterRegistry,
  private val sheetPresenterRegistry: SheetPresenterRegistry,
) : NavigatorPresenter {
  @Composable
  override fun model(
    initialScreen: Screen,
    onExit: () -> Unit,
  ): ScreenModel {
    val navigator = remember(initialScreen.key) {
      NavigatorImpl(initialScreen, onExit)
    }

    val currentScreenState by remember(navigator) {
      navigator.currentScreenState
    }.collectAsState()

    return when (val screen = currentScreenState.screen) {
      // SimpleScreen does not have a ScreenPresenter, we can directly call its model
      is SimpleScreen -> screen.model(navigator)
      else -> {
        // Fetch and use the ScreenPresenter from the registry for this screen.
        val screenPresenter = remember(screen.key) {
          screenPresenterRegistry.get(screen)
        }

        val screenModel = screenPresenter.model(navigator, screen)

        // We fetch a sheet from the registry if the current screen has a sheet.
        // If it does not, we use the screenModel's bottomSheetModel if one is shown without
        // navigator
        val sheetModel = currentScreenState.sheet?.let {
          val sheetPresenter = remember(it.key) {
            sheetPresenterRegistry.get(it)
          }
          sheetPresenter.model(navigator, it)
        } ?: screenModel.bottomSheetModel

        screenModel.copy(bottomSheetModel = sheetModel)
      }
    }
  }
}
