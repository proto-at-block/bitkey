package build.wallet.statemachine.fwup

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ScreenColorMode.Dark
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle.FullScreen
import build.wallet.ui.app.nfc.FwupNfcScreen
import kotlin.math.roundToInt

data class FwupNfcBodyModel(
  val onCancel: (() -> Unit)?,
  val status: Status,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo?,
) : BodyModel() {
  /**
   * Convenience method to wrap NFC screen model into full screen model.
   */
  fun asFullScreen() =
    ScreenModel(
      body = this,
      presentationStyle = FullScreen,
      colorMode = Dark
    )

  fun asPlatformNfcScreen() =
    ScreenModel(
      body = this,
      presentationStyle = FullScreen,
      colorMode = Dark,
      platformNfcScreen = true
    )

  @Composable
  override fun render(modifier: Modifier) {
    FwupNfcScreen(modifier, model = this)
  }

  sealed interface Status {
    val text: String

    data class Searching(
      override val text: String = "Hold device here behind phone",
    ) : Status

    data class InProgress(
      override val text: String = "Updating...",
      // Progress in range 0.0...100.00
      val fwupProgress: Float,
    ) : Status {
      val progressText = "${fwupProgress.roundToInt()}%"
      val progressPercentage = fwupProgress / 100
    }

    data class LostConnection(
      override val text: String = "Device no longer detected,\nhold device to phone",
      // Progress in range 0.0...100.00
      val fwupProgress: Float,
    ) : Status {
      val progressPercentage = fwupProgress / 100
    }

    data class Success(
      override val text: String = "Successfully updated",
    ) : Status
  }
}
