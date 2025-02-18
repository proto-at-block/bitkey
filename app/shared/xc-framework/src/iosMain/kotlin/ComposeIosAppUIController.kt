package build.wallet.ios

import androidx.compose.ui.window.ComposeUIViewController
import build.wallet.platform.device.DeviceInfo
import build.wallet.platform.sensor.Accelerometer
import build.wallet.statemachine.root.AppUiStateMachine
import build.wallet.ui.app.App
import platform.UIKit.UIColor
import platform.UIKit.UIViewController

/**
 * Used to render the iOS app entirely with Compose Multiplatform UI.
 */
@Suppress("unused") // Used by iOS
class ComposeIosAppUIController(
  private val appUiStateMachine: AppUiStateMachine,
  private val deviceInfo: DeviceInfo,
  private val accelerometer: Accelerometer,
) {
  val viewController: UIViewController = ComposeUIViewController {

    val model = appUiStateMachine.model(Unit)
    App(
      model = model,
      deviceInfo = deviceInfo,
      accelerometer = accelerometer
    )
  }.also {
    // We set the background color to black to avoid white flashes on black screens
    // (nfc, onboarding, etc.) throughout the app.
    it.view.backgroundColor = UIColor.blackColor
  }
}
