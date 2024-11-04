package build.wallet.ios

import androidx.compose.ui.window.ComposeUIViewController
import build.wallet.di.ActivityComponent
import build.wallet.ui.app.App
import build.wallet.ui.app.AppUiModelMap
import platform.UIKit.UIViewController

class ComposeIosAppUIController(
  private val activityComponent: ActivityComponent,
) {
  val viewController: UIViewController = ComposeUIViewController {

    App(
      model = activityComponent.appUiStateMachine.model(Unit),
      uiModelMap = AppUiModelMap
    )

    activityComponent.biometricPromptUiStateMachine.model(Unit)?.let {
      App(
        model = it,
        uiModelMap = AppUiModelMap
      )
    }
  }
}
