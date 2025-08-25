package build.wallet.ui.app.settings.fingerprints

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetSuccessBodyModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun FingerprintResetSuccessPreview() {
  PreviewWalletTheme {
    FormScreen(
      model = FingerprintResetSuccessBodyModel(
        onDone = {}
      )
    )
  }
}
