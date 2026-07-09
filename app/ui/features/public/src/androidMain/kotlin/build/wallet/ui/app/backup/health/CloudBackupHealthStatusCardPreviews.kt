package build.wallet.ui.app.backup.health

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.cloud.health.CloudBackupHealthStatusTone
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun CloudBackupHealthStatusGood() {
  PreviewWalletTheme {
    CloudBackupHealthStatusCard(
      model =
        CloudBackupHealthStatusCardModelForPreview.copy(
          backupStatusActionButton = null,
          toolbarModel = null
        )
    )
  }
}

@Preview
@Composable
fun CloudBackupHealthStatusError() {
  PreviewWalletTheme {
    CloudBackupHealthStatusCard(
      model = CloudBackupHealthStatusCardModelForPreview.copy(
        toolbarModel = null,
        backupStatus = CloudBackupHealthStatusProblemListItemForPreview,
        statusTextOverride = "No backup found",
        statusToneOverride = CloudBackupHealthStatusTone.DANGER,
        backupStatusActionButton = CloudBackupHealthStatusActionButtonForPreview
      )
    )
  }
}

@Preview
@Composable
fun CloudBackupHealthStatusEEKGood() {
  PreviewWalletTheme {
    CloudBackupHealthStatusCard(
      model =
        CloudBackupHealthStatusCardEekModelForPreview.copy(
          backupStatusActionButton = null
        )
    )
  }
}

@Preview
@Composable
fun CloudBackupHealthStatusEEKError() {
  PreviewWalletTheme {
    CloudBackupHealthStatusCard(
      model =
        CloudBackupHealthStatusCardEekModelForPreview.copy(
          statusTextOverride = "No backup found",
          statusToneOverride = CloudBackupHealthStatusTone.DANGER,
          backupStatusActionButton = CloudBackupHealthStatusActionButtonForPreview,
          backupStatus = CloudBackupHealthStatusEekProblemListItemForPreview
        )
    )
  }
}
