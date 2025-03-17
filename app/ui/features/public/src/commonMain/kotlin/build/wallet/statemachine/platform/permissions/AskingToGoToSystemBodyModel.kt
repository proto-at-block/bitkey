package build.wallet.statemachine.platform.permissions

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel
import build.wallet.ui.app.platform.permissions.AskingToGoToSystemScreen

data class AskingToGoToSystemBodyModel(
  val title: String,
  val explanation: String,
  val onGoToSetting: () -> Unit,
  override val onBack: () -> Unit,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null,
) : BodyModel() {
  @Composable
  override fun render(modifier: Modifier) {
    AskingToGoToSystemScreen(modifier, model = this)
  }
}
