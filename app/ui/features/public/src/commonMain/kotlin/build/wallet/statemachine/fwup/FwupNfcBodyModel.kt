package build.wallet.statemachine.fwup

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle.FullScreen
import build.wallet.ui.app.nfc.FwupNfcScreen
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference
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
      themePreference = ThemePreference.Manual(Theme.DARK)
    )

  fun asPlatformNfcScreen() =
    ScreenModel(
      body = this,
      presentationStyle = FullScreen,
      themePreference = ThemePreference.Manual(Theme.DARK),
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
      val currentMcuRole: build.wallet.firmware.McuRole? = null, // null for W1 compatibility
      val mcuIndex: Int = 0, // 0-based index
      val totalMcus: Int = 1, // 1 for W1, 2+ for W3
      val fwupProgress: Float, // Progress in range 0.0...100.00
    ) : Status {
      override val text: String = when {
        totalMcus == 1 -> "Updating..."
        else -> "Updating (${mcuIndex + 1}/$totalMcus)..."
      }
      val progressText = "${fwupProgress.roundToInt()}%"
      val progressPercentage = fwupProgress / 100
    }

    data class LostConnection(
      val currentMcuRole: build.wallet.firmware.McuRole? = null, // null for W1 compatibility
      val mcuIndex: Int = 0, // 0-based index
      val totalMcus: Int = 1, // 1 for W1, 2+ for W3
      val fwupProgress: Float, // Progress in range 0.0...100.00
    ) : Status {
      override val text: String = when {
        totalMcus == 1 -> "Device no longer detected,\nhold device to phone"
        else -> "Lost connection during update (${mcuIndex + 1}/$totalMcus),\nhold device to phone"
      }
      val progressPercentage = fwupProgress / 100
    }

    data class Success(
      override val text: String = "Successfully updated",
    ) : Status
  }
}
