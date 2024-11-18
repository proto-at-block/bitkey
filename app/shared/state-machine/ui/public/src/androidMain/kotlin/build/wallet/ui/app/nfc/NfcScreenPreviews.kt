package build.wallet.ui.app.nfc

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.nfc.NfcBodyModel
import build.wallet.statemachine.nfc.NfcBodyModel.Status.*
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun NfcScreenSearchingPreview() {
  PreviewWalletTheme {
    NfcScreenInternal(
      model =
        NfcBodyModel(
          text = "Hold device here behind phone",
          status = Searching { },
          eventTrackerScreenInfo = null
        )
    )
  }
}

@Preview
@Composable
fun NfcScreenConnectedPreview() {
  PreviewWalletTheme {
    NfcScreenInternal(
      model =
        NfcBodyModel(
          text = "Hold device here behind phone",
          status = Connected(onCancel = {}),
          eventTrackerScreenInfo = null
        )
    )
  }
}

@Preview
@Composable
fun NfcScreenConnectedWithSpinnerPreview() {
  PreviewWalletTheme {
    NfcScreenInternal(
      model =
        NfcBodyModel(
          text = "This can take up to 1 minute…",
          status = Connected(onCancel = {}, showProgressSpinner = true),
          eventTrackerScreenInfo = null
        )
    )
  }
}

@Preview
@Composable
fun NfcScreenSuccessPreview() {
  PreviewWalletTheme {
    NfcScreenInternal(
      model =
        NfcBodyModel(
          text = "Success",
          status = Success,
          eventTrackerScreenInfo = null
        )
    )
  }
}
