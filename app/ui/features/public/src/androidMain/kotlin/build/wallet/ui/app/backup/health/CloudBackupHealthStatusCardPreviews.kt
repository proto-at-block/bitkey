package build.wallet.ui.app.backup.health

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.cloud.health.CloudBackupHealthStatusCardType
import build.wallet.statemachine.core.Icon
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
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
        backupStatus = ListItemModel(
          title = "Problem with Google account access",
          trailingAccessory = ListItemAccessory.IconAccessory(Icon.SmallIconWarning)
        )
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
        CloudBackupHealthStatusCardModelForPreview.copy(
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
        CloudBackupHealthStatusCardModelForPreview.copy(
          backupStatusActionButton =
            ButtonModel(
              text = "Back up now",
              size = Footer,
              treatment = ButtonModel.Treatment.Primary,
              onClick = StandardClick {}
            ),
          backupStatus = ListItemModel(
            title = "Problem with Google account access",
            trailingAccessory = ListItemAccessory.IconAccessory(Icon.SmallIconWarning)
          ),
          type = CloudBackupHealthStatusCardType.EEK_BACKUP
        )
    )
  }
}
