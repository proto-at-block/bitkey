package build.wallet.statemachine.send

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.ui.app.qrcode.QrCodeScanScreen
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference

data class QrCodeScanBodyModel(
  val onQrCodeScanned: (String) -> Unit,
  val onClose: () -> Unit,
  val headline: String? = null,
  val reticleLabel: String? = null,
  private val primaryButtonData: ButtonDataModel? = null,
  private val secondaryButtonData: ButtonDataModel? = null,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null,
) : BodyModel() {
  val primaryButton = primaryButtonData.toButton()
  val secondaryButton = secondaryButtonData.toButton()

  /**
   * Convenience method to this model into full screen model.
   */
  fun asFullScreen() =
    ScreenModel(
      body = this,
      presentationStyle = ScreenPresentationStyle.FullScreen,
      themePreference = ThemePreference.Manual(Theme.DARK)
    )

  /**
   * Convert a button data model to one that can be used on the scan screen.
   */
  private fun ButtonDataModel?.toButton() =
    this?.let { button ->
      ButtonModel(
        text = button.text,
        onClick = StandardClick(button.onClick),
        isLoading = button.isLoading,
        treatment = ButtonModel.Treatment.Translucent,
        size = ButtonModel.Size.Footer,
        leadingIcon = button.leadingIcon
      )
    }

  @Composable
  override fun render(modifier: Modifier) {
    QrCodeScanScreen(modifier, model = this)
  }
}
