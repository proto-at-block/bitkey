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
            designSystemV2StatusText = "No backup found",
            designSystemV2StatusTone = CloudBackupHealthStatusTone.DANGER,
            backupStatusActionButton = CloudBackupHealthStatusActionButtonForPreview
          ),
        eekBackupStatusCard = CloudBackupHealthStatusCardEekModelForPreview
      )
  )
}

@Preview
@Composable
fun CloudBackupHealthDashboardScreenDesignSystemV2Preview() {
  PreviewWalletTheme {
    CloudBackupHealthDashboardScreen(
      model =
        CloudBackupHealthDashboardBodyModel(
          onBack = {},
          appKeyBackupStatusCard =
            CloudBackupHealthStatusCardModelForPreview.copy(
              backupStatus = CloudBackupHealthStatusProblemListItemForPreview,
              designSystemV2StatusText = "No backup found",
              designSystemV2StatusTone = CloudBackupHealthStatusTone.DANGER,
              backupStatusActionButton = CloudBackupHealthStatusActionButtonForPreview
            ),
          eekBackupStatusCard = CloudBackupHealthStatusCardEekModelForPreview
        )
    )
  }
}
