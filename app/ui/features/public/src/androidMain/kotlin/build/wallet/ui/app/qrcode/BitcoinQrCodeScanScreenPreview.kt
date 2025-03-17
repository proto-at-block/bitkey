package build.wallet.ui.app.qrcode

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.send.BitcoinQrCodeScanBodyModel
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun PreviewQrCodeScanScreenWithoutPasteAddressButton() {
  PreviewWalletTheme(backgroundColor = Color.Transparent) {
    QrCodeScanViewFinder()
    QrCodeScanWidgets(
      model =
        BitcoinQrCodeScanBodyModel(
          showSendToCopiedAddressButton = false,
          onQrCodeScanned = {},
          onEnterAddressClick = {},
          onClose = {},
          onSendToCopiedAddressClick = {}
        )
    )
  }
}

@Preview
@Composable
fun PreviewQrCodeScanScreenWithPasteAddressButton() {
  PreviewWalletTheme(backgroundColor = Color.Transparent) {
    QrCodeScanViewFinder()
    QrCodeScanWidgets(
      model =
        BitcoinQrCodeScanBodyModel(
          showSendToCopiedAddressButton = true,
          onQrCodeScanned = {},
          onEnterAddressClick = {},
          onClose = {},
          onSendToCopiedAddressClick = {}
        )
    )
  }
}
