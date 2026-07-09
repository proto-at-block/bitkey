package build.wallet.statemachine.nfc

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import bitkey.account.HardwareType
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.platform.device.DevicePlatform
import build.wallet.statemachine.automations.AutomaticUiTests
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle.ModalFullScreen
import build.wallet.ui.app.nfc.NfcScreen

/**
 * Body model for NFC screens used across various NFC flows (proof of possession, etc.)
 *
 * @property hardwareType The type of hardware device being tapped. Used to display
 * the appropriate device imagery (W1 vs W3) during the NFC session.
 */
data class NfcBodyModel(
  val text: String,
  val status: Status,
  val hardwareType: HardwareType = HardwareType.W3,
  val showNativeSheetOnIos: Boolean = true,
  val onHelpClick: (() -> Unit)? = null,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo?,
) : BodyModel(), AutomaticUiTests {
  /**
   * Convenience method to wrap NFC screen model into a platform NFC screen.
   */
  fun asPlatformNfcScreen(
    devicePlatform: DevicePlatform,
  ) =
    ScreenModel(
      body = this,
      presentationStyle = ModalFullScreen,
      themePreference = nfcThemePreference(
        devicePlatform = devicePlatform,
        followSystemOnIos = showNativeSheetOnIos
      ),
      platformNfcScreen = true
    )

  sealed interface Status {
    data class Searching(
      val onCancel: () -> Unit,
    ) : Status

    data class Connected(
      val onCancel: () -> Unit,
      /** Whether we want to show an indeterminate progress spinner (on Android) during the NFC operation */
      val showProgressSpinner: Boolean = false,
    ) : Status

    data object Success : Status
  }

  override fun automateNextPrimaryScreen() {
    // No-op necessary when using fake hardware.
  }

  @Composable
  override fun render(modifier: Modifier) {
    NfcScreen(modifier, model = this)
  }
}
