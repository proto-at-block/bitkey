package build.wallet.statemachine.cloud.health

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel
import build.wallet.ui.app.backup.health.CloudBackupHealthDashboardScreen

data class CloudBackupHealthDashboardBodyModel(
  override val onBack: () -> Unit,
  val appKeyBackupStatusCard: CloudBackupHealthStatusCardModel,
  val eekBackupStatusCard: CloudBackupHealthStatusCardModel? = null,
) : BodyModel() {
  // TODO(BKR-868): implement analytics
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null

  @Composable
  override fun render(modifier: Modifier) {
    CloudBackupHealthDashboardScreen(modifier, model = this)
  }
}
