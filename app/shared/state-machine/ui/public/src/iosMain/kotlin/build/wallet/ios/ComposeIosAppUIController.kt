package build.wallet.ios

import androidx.compose.ui.window.ComposeUIViewController
import build.wallet.statemachine.root.AppUiStateMachine
import build.wallet.ui.app.App
import build.wallet.ui.app.AppUiModelMap
import platform.UIKit.UIViewController

/**
 * Used to render the iOS app entirely with Compose Multiplatform UI.
 */
@Suppress("unused") // Used by iOS
class ComposeIosAppUIController(
  private val appUiStateMachine: AppUiStateMachine,
) {
  val viewController: UIViewController = ComposeUIViewController {

    App(
      model = appUiStateMachine.model(Unit),
      uiModelMap = AppUiModelMap
    )
  }
}
