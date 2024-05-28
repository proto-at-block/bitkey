package bitkey.sample.ui.error

import androidx.compose.runtime.Composable
import build.wallet.statemachine.core.ScreenModel
import build.wallet.ui.framework.Navigator
import build.wallet.ui.framework.Screen
import build.wallet.ui.framework.SimpleScreen

data class ErrorScreen(
  val message: String,
  /**
   * The screen to navigate to when the user exits the error screen.
   */
  val exitScreen: Screen,
) : SimpleScreen {
  @Composable
  override fun model(navigator: Navigator): ScreenModel {
    return ErrorBodyModel(
      message = message,
      onBack = {
        navigator.goTo(exitScreen)
      }
    ).asModalScreen()
  }
}
