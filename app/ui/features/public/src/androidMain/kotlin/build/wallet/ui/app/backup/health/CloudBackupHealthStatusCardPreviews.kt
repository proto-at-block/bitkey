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
fun CloudBackupHealthStatusGoodDesignSystemV2() {
  PreviewWalletTheme(designSystemUpdatesEnabled = true) {
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
        designSystemV2StatusText = "No backup found",
        designSystemV2StatusTone = CloudBackupHealthStatusTone.DANGER,
        backupStatusActionButton = CloudBackupHealthStatusActionButtonForPreview
      )
    )
  }
}

@Preview
@Composable
fun CloudBackupHealthStatusErrorDesignSystemV2() {
  PreviewWalletTheme(designSystemUpdatesEnabled = true) {
    CloudBackupHealthStatusCard(
      model = CloudBackupHealthStatusCardModelForPreview.copy(
        toolbarModel = null,
        backupStatus = CloudBackupHealthStatusProblemListItemForPreview,
        designSystemV2StatusText = "No backup found",
        designSystemV2StatusTone = CloudBackupHealthStatusTone.DANGER,
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
fun CloudBackupHealthStatusEEKGoodDesignSystemV2() {
  PreviewWalletTheme(designSystemUpdatesEnabled = true) {
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
          designSystemV2StatusText = "No backup found",
          designSystemV2StatusTone = CloudBackupHealthStatusTone.DANGER,
          backupStatusActionButton = CloudBackupHealthStatusActionButtonForPreview,
          backupStatus = CloudBackupHealthStatusEekProblemListItemForPreview
        )
    )
  }
}

@Preview
@Composable
fun CloudBackupHealthStatusEEKErrorDesignSystemV2() {
  PreviewWalletTheme(designSystemUpdatesEnabled = true) {
    CloudBackupHealthStatusCard(
      model =
        CloudBackupHealthStatusCardEekModelForPreview.copy(
          designSystemV2StatusText = "No backup found",
          designSystemV2StatusTone = CloudBackupHealthStatusTone.DANGER,
          backupStatusActionButton = CloudBackupHealthStatusActionButtonForPreview,
          backupStatus = CloudBackupHealthStatusEekProblemListItemForPreview
        )
    )
  }
}
