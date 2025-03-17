package build.wallet.ui.app.nfc

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.fwup.FwupNfcBodyModel
import build.wallet.statemachine.fwup.FwupNfcBodyModel.Status.*
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
internal fun FwupNfcSearchingPreview() {
  PreviewWalletTheme {
    FwupNfcScreenInternal(
      model =
        FwupNfcBodyModel(
          onCancel = {},
          status = Searching(),
          eventTrackerScreenInfo = null
        )
    )
  }
}

@Preview
@Composable
internal fun FwupNfcProgressPreview() {
  PreviewWalletTheme {
    FwupNfcScreenInternal(
      model =
        FwupNfcBodyModel(
          onCancel = {},
          status = InProgress(fwupProgress = 5f),
          eventTrackerScreenInfo = null
        )
    )
  }
}

@Preview
@Composable
internal fun FwupNfcLostConnectionPreview() {
  PreviewWalletTheme {
    FwupNfcScreenInternal(
      model =
        FwupNfcBodyModel(
          onCancel = {},
          status = LostConnection(fwupProgress = 5f),
          eventTrackerScreenInfo = null
        )
    )
  }
}

@Preview
@Composable
internal fun FwupNfcSuccessPreview() {
  PreviewWalletTheme {
    FwupNfcScreenInternal(
      model =
        FwupNfcBodyModel(
          onCancel = null,
          status = Success(),
          eventTrackerScreenInfo = null
        )
    )
  }
}
