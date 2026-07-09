package build.wallet.ui.app.send

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationCanceledScreenModel
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationScreenModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun HardwareConfirmationScreenPreview() {
  PreviewWalletTheme {
    FormScreen(
      model = HardwareConfirmationScreenModel(
        onBack = {},
        onConfirm = {}
      )
    )
  }
}

@Preview
@Composable
fun HardwareConfirmationCanceledScreenPreview() {
  PreviewWalletTheme {
    FormScreen(
      model = HardwareConfirmationCanceledScreenModel(
        onBack = {}
      )
    )
  }
}
