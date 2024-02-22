package build.wallet.ui.app.settings.electrum

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.settings.full.electrum.CustomElectrumServerBodyModel
import build.wallet.statemachine.settings.full.electrum.disableCustomElectrumServerAlertModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.components.alertdialog.AlertDialog
import build.wallet.ui.components.switch.SwitchCard
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.switch.SwitchCardModel.ActionRow
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.tooling.PreviewWalletTheme

@Composable
fun CustomElectrumServerScreen(model: CustomElectrumServerBodyModel) {
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

@Preview
@Composable
fun CustomElectrumServerScreenDisabledPreview() {
  PreviewWalletTheme {
    CustomElectrumServerScreen(
      CustomElectrumServerBodyModel(
        onBack = {},
        switchIsChecked = false,
        electrumServerRow = null,
        onSwitchCheckedChange = {},
        disableAlertModel = null
      )
    )
  }
}

@Preview
@Composable
fun CustomElectrumServerScreenEnabledPreview() {
  PreviewWalletTheme {
    CustomElectrumServerScreen(
      CustomElectrumServerBodyModel(
        onBack = {},
        switchIsChecked = true,
        electrumServerRow =
          ActionRow(
            title = "Connected to: ",
            sideText = "ssl://bitkey.mempool.space:50002",
            onClick = {}
          ),
        onSwitchCheckedChange = {},
        disableAlertModel = null
      )
    )
  }
}

@Preview
@Composable
fun CustomElectrumServerScreenEnabledWithDisablingDialogPreview() {
  PreviewWalletTheme {
    CustomElectrumServerScreen(
      CustomElectrumServerBodyModel(
        onBack = {},
        switchIsChecked = false,
        electrumServerRow =
          ActionRow(
            title = "Connected to: ",
            sideText = "ssl://bitkey.mempool.space:50002",
            onClick = {}
          ),
        onSwitchCheckedChange = {},
        disableAlertModel =
          disableCustomElectrumServerAlertModel(
            onDismiss = {},
            onConfirm = {}
          )
      )
    )
  }
}
