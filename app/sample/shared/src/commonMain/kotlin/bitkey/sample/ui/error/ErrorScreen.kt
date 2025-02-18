package bitkey.sample.ui.error

import androidx.compose.runtime.Composable
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.Screen
import bitkey.ui.framework.SimpleScreen
import build.wallet.statemachine.core.ScreenModel

data class ErrorScreen(
  val message: String,
  /**
   * The screen to navigate to when the user exits the error screen.
   */
  val origin: Screen,
) : SimpleScreen {
  @Composable
  override fun model(navigator: Navigator): ScreenModel {
    return ErrorBodyModel(
      message = message,
      onBack = {
        navigator.goTo(origin)
      }
    ).asModalScreen()
  }
}
