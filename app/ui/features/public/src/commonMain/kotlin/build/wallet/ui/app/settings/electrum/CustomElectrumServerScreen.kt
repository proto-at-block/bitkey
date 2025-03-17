package build.wallet.ui.app.settings.electrum

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.statemachine.settings.full.electrum.CustomElectrumServerBodyModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.components.alertdialog.AlertDialog
import build.wallet.ui.components.switch.SwitchCard
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

@Composable
fun CustomElectrumServerScreen(
  modifier: Modifier = Modifier,
  model: CustomElectrumServerBodyModel,
) {
  val onBack =
    when (val disableAlertModel = model.disableAlertModel) {
      null -> model.onBack
      else -> disableAlertModel.onDismiss
    }

  FormScreen(
    modifier = modifier,
    onBack = onBack,
    toolbarContent = {
      Toolbar(
        model =
          ToolbarModel(
            leadingAccessory = BackAccessory(onClick = model.onBack)
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
