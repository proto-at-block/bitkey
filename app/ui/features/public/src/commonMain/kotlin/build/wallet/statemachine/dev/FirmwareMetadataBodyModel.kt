package build.wallet.statemachine.dev

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel
import build.wallet.ui.app.dev.FirmwareMetadataScreen

data class FirmwareMetadataModel(
  val activeSlot: String,
  val gitId: String,
  val gitBranch: String,
  val version: String,
  val build: String,
  val timestamp: String,
  val hash: String,
  val hwRevision: String,
)

data class FirmwareMetadataBodyModel(
  override val onBack: () -> Unit,
  val onFirmwareMetadataRefreshClick: () -> Unit,
  val firmwareMetadataModel: FirmwareMetadataModel?,
  // This is only used by the debug menu and will soon be removed,
  // it doesn't need a screen ID
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null,
) : BodyModel() {
  @Composable
  override fun render(modifier: Modifier) {
    FirmwareMetadataScreen(modifier, model = this)
  }
}
