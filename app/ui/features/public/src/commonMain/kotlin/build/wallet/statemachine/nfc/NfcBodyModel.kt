package build.wallet.statemachine.nfc

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.automations.AutomaticUiTests
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle.FullScreen
import build.wallet.ui.app.nfc.NfcScreen
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference

data class NfcBodyModel(
  val text: String,
  val status: Status,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo?,
) : BodyModel(), AutomaticUiTests {
  /**
   * Convenience method to wrap NFC screen model into full screen model
   * and mark it as [ScreenModel.platformNfcScreen] so it will not be
   * displayed when platform provided UI will be shown instead.
   */
  fun asPlatformNfcScreen() =
    ScreenModel(
      body = this,
      presentationStyle = FullScreen,
      themePreference = ThemePreference.Manual(Theme.DARK),
      platformNfcScreen = true
    )

  sealed class Status {
    data class Searching(
      val onCancel: () -> Unit,
    ) : Status()

    data class Connected(
      val onCancel: () -> Unit,
      /** Whether we want to show an indeterminate progress spinner (on Android) during the NFC operation */
      val showProgressSpinner: Boolean = false,
    ) : Status()

    data object Success : Status()
  }

  override fun automateNextPrimaryScreen() {
    // No-op necessary when using fake hardware.
  }

  @Composable
  override fun render(modifier: Modifier) {
    NfcScreen(modifier, model = this)
  }
}
