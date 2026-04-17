package build.wallet.ui.app.nfc

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId
import build.wallet.statemachine.nfc.HardwareConfirmationResultBodyModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
internal fun HardwareConfirmationResultPreview() {
  PreviewWalletTheme {
    FormScreen(
      model = HardwareConfirmationResultBodyModel(
        headline = "Review transaction on Bitkey",
        subline = "Before sending, use your Bitkey device to review the transaction details.",
        buttonText = "Got it",
        onAcknowledge = {},
        eventTrackerScreenId = NfcEventTrackerScreenId.NFC_CONFIRMATION_PENDING
      )
    )
  }
}
