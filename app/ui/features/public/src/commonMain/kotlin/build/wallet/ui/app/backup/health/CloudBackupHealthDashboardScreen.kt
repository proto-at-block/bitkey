package build.wallet.ui.app.backup.health

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.statemachine.core.form.FormMainContentVerticalAlignment
import build.wallet.statemachine.core.form.FormScreenLayoutModel
import build.wallet.statemachine.core.form.FormScreenTitleModel
import build.wallet.statemachine.cloud.health.CloudBackupHealthDashboardBodyModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

@Composable
fun CloudBackupHealthDashboardScreen(
  modifier: Modifier = Modifier,
  model: CloudBackupHealthDashboardBodyModel,
) {
  FormScreen(
    modifier = modifier,
    onBack = model.onBack,
    toolbarModel = ToolbarModel(leadingAccessory = BackAccessory(model.onBack)),
    toolbarContent = {
      Toolbar(
        model =
          ToolbarModel(
            leadingAccessory = BackAccessory(model.onBack),
            middleAccessory = ToolbarMiddleAccessoryModel("Cloud Backup")
          )
      )
    },
    screenTitle = FormScreenTitleModel(title = "Cloud Backup"),
    layout = FormScreenLayoutModel.LargeTitle(
      contentSpacing = 40,
      scrollable = false,
      mainContentVerticalAlignment = FormMainContentVerticalAlignment.TOP
    ),
    mainContent = {
      CloudBackupHealthStatusCard(model = model.appKeyBackupStatusCard)
      model.eekBackupStatusCard?.let {
        CloudBackupHealthStatusCard(model = it)
      }
    }
  )
}
