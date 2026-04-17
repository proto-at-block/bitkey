package build.wallet.ui.app.backup.health

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.cloud.health.CloudBackupHealthDashboardBodyModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.app.core.form.FormScreenContentVerticalAlignment
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled

@Composable
fun CloudBackupHealthDashboardScreen(
  modifier: Modifier = Modifier,
  model: CloudBackupHealthDashboardBodyModel,
) {
  val isDesignSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current

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
    designSystemV2Title = "Cloud Backup",
    designSystemV2ContentSpacing = 40,
    designSystemV2Scrollable = false,
    designSystemV2MainContentAlignment = FormScreenContentVerticalAlignment.Top,
    mainContent = {
      CloudBackupHealthStatusCard(model = model.appKeyBackupStatusCard)
      if (!isDesignSystemV2Enabled) {
        Spacer(modifier = Modifier.height(20.dp))
      }
      model.eekBackupStatusCard?.let {
        CloudBackupHealthStatusCard(model = it)
      }
    }
  )
}
