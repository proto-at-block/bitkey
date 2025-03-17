package build.wallet

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.sensor.Accelerometer
import build.wallet.statemachine.root.AppUiStateMachine
import build.wallet.ui.app.App
import build.wallet.ui.theme.ThemePreferenceService
import platform.UIKit.UIColor
import platform.UIKit.UIViewController

@BitkeyInject(ActivityScope::class)
class ComposeIosAppUIControllerImpl(
  private val appUiStateMachine: AppUiStateMachine,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val accelerometer: Accelerometer,
  private val themePreferenceService: ThemePreferenceService,
) : ComposeIosAppUIController {
  override val viewController: UIViewController = ComposeUIViewController {

    val model = appUiStateMachine.model(Unit)
    val deviceInfo = remember { deviceInfoProvider.getDeviceInfo() }
    App(
      model = model,
      deviceInfo = deviceInfo,
      accelerometer = accelerometer,
      themePreferenceService = themePreferenceService
    )
  }.also {
    // We set the background color to black to avoid white flashes on black screens
    // (nfc, onboarding, etc.) throughout the app.
    it.view.backgroundColor = UIColor.blackColor
  }
}
