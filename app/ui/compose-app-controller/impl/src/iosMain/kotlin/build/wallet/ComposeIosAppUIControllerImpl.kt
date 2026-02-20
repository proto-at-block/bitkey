package build.wallet

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.DesignSystemUpdatesFeatureFlag
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.haptics.Haptics
import build.wallet.platform.sensor.Accelerometer
import build.wallet.statemachine.root.AppUiStateMachine
import build.wallet.ui.app.App
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference
import build.wallet.ui.theme.ThemePreferenceService
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIColor
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIViewController

@BitkeyInject(ActivityScope::class)
class ComposeIosAppUIControllerImpl(
  private val appUiStateMachine: AppUiStateMachine,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val accelerometer: Accelerometer,
  private val themePreferenceService: ThemePreferenceService,
  private val haptics: Haptics,
  private val designSystemUpdatesFeatureFlag: DesignSystemUpdatesFeatureFlag,
) : ComposeIosAppUIController {
  @OptIn(ExperimentalForeignApi::class)
  override val viewController: UIViewController = ComposeUIViewController {

    val model = appUiStateMachine.model(Unit)
    val deviceInfo = remember { deviceInfoProvider.getDeviceInfo() }

    // Observe theme preference to apply override to view controller
    // This ensures native iOS components (alerts, pickers, etc.) respect the app's theme
    val themePreference by themePreferenceService.themePreference()
      .collectAsState(initial = ThemePreference.System)

    LaunchedEffect(themePreference) {
      viewController.overrideUserInterfaceStyle = when (val pref = themePreference) {
        // When System is selected, don't override - let iOS handle it automatically
        is ThemePreference.System -> UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
        is ThemePreference.Manual -> when (pref.value) {
          Theme.LIGHT -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
          Theme.DARK -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
        }
      }
    }

    App(
      model = model,
      deviceInfo = deviceInfo,
      accelerometer = accelerometer,
      themePreferenceService = themePreferenceService,
      haptics = haptics,
      designSystemUpdatesEnabled = designSystemUpdatesFeatureFlag.flagValue()
    )
  }.also {
    // We set the background color to black to avoid white flashes on black screens
    // (nfc, onboarding, etc.) throughout the app.
    it.view.backgroundColor = UIColor.blackColor
  }
}
