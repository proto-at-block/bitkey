package build.wallet.ui.app.backup.health

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.cloud.health.CloudBackupHealthDashboardBodyModel
import build.wallet.statemachine.cloud.health.CloudBackupHealthStatusTone
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun CloudBackupHealthDashboardScreenPreview() {
  CloudBackupHealthDashboardScreen(
    model =
      CloudBackupHealthDashboardBodyModel(
        onBack = {},
        appKeyBackupStatusCard =
          CloudBackupHealthStatusCardModelForPreview.copy(
            backupStatus = CloudBackupHealthStatusProblemListItemForPreview,
            statusTextOverride = "No backup found",
            statusToneOverride = CloudBackupHealthStatusTone.DANGER,
            backupStatusActionButton = CloudBackupHealthStatusActionButtonForPreview
          ),
        eekBackupStatusCard = CloudBackupHealthStatusCardEekModelForPreview
      )
  )
}
