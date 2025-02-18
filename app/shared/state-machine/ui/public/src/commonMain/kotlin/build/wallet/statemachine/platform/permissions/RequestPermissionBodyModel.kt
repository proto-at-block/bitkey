package build.wallet.statemachine.platform.permissions

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel
import build.wallet.ui.app.platform.permissions.RequestPermissionScreen

data class RequestPermissionBodyModel(
  val title: String,
  val explanation: String,
  val showingSystemPermission: Boolean,
  override val onBack: () -> Unit,
  val onRequest: () -> Unit,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null,
) : BodyModel() {
  @Composable
  override fun render(modifier: Modifier) {
    RequestPermissionScreen(modifier, model = this)
  }
}
