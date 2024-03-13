package build.wallet.ui.app.demo

import androidx.compose.runtime.Composable
import build.wallet.statemachine.demo.DemoModeConfigBodyModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.components.alertdialog.AlertDialog
import build.wallet.ui.components.switch.SwitchCard
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

@Composable
fun DemoModeConfigScreen(model: DemoModeConfigBodyModel) {
  val onBack =
    when (val disableAlertModel = model.disableAlertModel) {
      null -> model.onBack
      else -> disableAlertModel.onDismiss
    }

  FormScreen(
    onBack = onBack,
    toolbarContent = {
      Toolbar(
        model =
          ToolbarModel(
            leadingAccessory = ToolbarAccessoryModel.IconAccessory.BackAccessory(onClick = model.onBack)
          )
      )
    },
    mainContent = {
      SwitchCard(model = model.switchCardModel)

      model.disableAlertModel?.let { alertModel ->
        AlertDialog(alertModel)
      }
    }
  )
}
