package build.wallet.ui.app.backup.health

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.cloud.health.CloudBackupHealthDashboardBodyModel

@Preview
@Composable
fun CloudBackupHealthDashboardScreenPreview() {
  CloudBackupHealthDashboardScreen(
    model =
      CloudBackupHealthDashboardBodyModel(
        onBack = {},
        mobileKeyBackupStatusCard = CloudBackupHealthStatusCardModelForPreview,
        eakBackupStatusCard = CloudBackupHealthStatusCardModelForPreview
      )
  )
}
