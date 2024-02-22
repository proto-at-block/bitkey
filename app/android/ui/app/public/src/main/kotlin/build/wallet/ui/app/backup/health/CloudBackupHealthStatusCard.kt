package build.wallet.ui.app.backup.health

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.cloud.health.CloudBackupHealthStatusCardModel
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.SmallIconCheckFilled
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.card.Card
import build.wallet.ui.components.header.Header
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.components.list.ListItem
import build.wallet.ui.model.Click.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarModel

@Composable
internal fun CloudBackupHealthStatusCard(model: CloudBackupHealthStatusCardModel) {
  Card(horizontalAlignment = Alignment.CenterHorizontally) {
    Header(model = model.headerModel)
    Spacer(Modifier.height(20.dp))
    Divider()
    ListItem(model = model.backupStatus)
    model.backupStatusActionButton?.let {
      Button(it)
      Spacer(Modifier.height(20.dp))
    }
  }
}

internal val CloudBackupHealthStatusCardModelForPreview =
  CloudBackupHealthStatusCardModel(
    toolbarModel = ToolbarModel(),
    headerModel =
      FormHeaderModel(
        icon = Icon.LargeIconWarningFilled,
        headline = "Title",
        subline = "Subline",
        alignment = FormHeaderModel.Alignment.CENTER
      ),
    backupStatus = ListItemModel(
      title = "Backup status title",
      secondaryText = "Backup status description",
      trailingAccessory = ListItemAccessory.IconAccessory(SmallIconCheckFilled)
    ),
    backupStatusActionButton =
      ButtonModel(
        text = "Back up now",
        size = Footer,
        onClick = StandardClick {}
      )
  )

@Preview
@Composable
internal fun CloudBackupHealthStatusCardWithButtonPreview() {
  CloudBackupHealthStatusCard(model = CloudBackupHealthStatusCardModelForPreview)
}

@Preview
@Composable
internal fun CloudBackupHealthStatusCardWithoutButtonPreview() {
  CloudBackupHealthStatusCard(
    model =
      CloudBackupHealthStatusCardModelForPreview.copy(
        backupStatusActionButton = null
      )
  )
}
