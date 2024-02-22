package build.wallet.statemachine.cloud.health

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel

// TODO(BKR-805): implement UI as per design specs
data class CloudBackupHealthDashboardBodyModel(
  override val onBack: () -> Unit,
  val mobileKeyBackupStatusCard: CloudBackupHealthStatusCardModel,
  val eakBackupStatusCard: CloudBackupHealthStatusCardModel? = null,
) : BodyModel() {
  // TODO(BKR-868): implement analytics
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null
}
