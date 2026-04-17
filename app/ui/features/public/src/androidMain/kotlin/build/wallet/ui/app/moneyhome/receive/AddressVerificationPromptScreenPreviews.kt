package build.wallet.ui.app.moneyhome.receive

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.receive.AddressVerificationPromptBodyModel
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun AddressVerificationPromptScreenPreview() {
  PreviewWalletTheme {
    AddressVerificationPromptBodyModel(
      onBack = {},
      onVerify = {},
      onSkip = {}
    ).render(Modifier)
  }
}
