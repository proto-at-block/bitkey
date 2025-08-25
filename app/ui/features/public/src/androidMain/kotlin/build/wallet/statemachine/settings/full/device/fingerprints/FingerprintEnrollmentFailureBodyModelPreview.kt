package build.wallet.statemachine.settings.full.device.fingerprints

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetEnrollmentFailureBodyModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun FingerprintEnrollmentFailureBodyModelPreview() {
  PreviewWalletTheme {
    FormScreen(
      model = FingerprintResetEnrollmentFailureBodyModel(
        onBackClick = {},
        onTryAgain = {}
      )
    )
  }
}
