package build.wallet.ui.app.settings.electrum

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.settings.full.electrum.CustomElectrumServerBodyModel
import build.wallet.statemachine.settings.full.electrum.disableCustomElectrumServerAlertModel
import build.wallet.ui.model.switch.SwitchCardModel.ActionRow
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun CustomElectrumServerScreenDisabledPreview() {
  PreviewWalletTheme {
    CustomElectrumServerScreen(
      model = CustomElectrumServerBodyModel(
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
      model = CustomElectrumServerBodyModel(
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
      model = CustomElectrumServerBodyModel(
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
