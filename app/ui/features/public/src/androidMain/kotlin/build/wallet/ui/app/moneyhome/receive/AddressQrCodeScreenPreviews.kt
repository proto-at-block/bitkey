package build.wallet.ui.app.moneyhome.receive

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.qr.QrCodeModel
import build.wallet.statemachine.receive.AddressQrCodeBodyModel
import build.wallet.statemachine.receive.AddressQrCodeBodyModel.Content.Error
import build.wallet.statemachine.receive.AddressQrCodeBodyModel.Content.QrCode
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun AddressQrCodeScreenPreview() {
  val address = "bc1q42lja79elem0anu8q8s3h2n687re9jax556pcc"
  PreviewWalletTheme {
    AddressQrCodeScreen(
      model = AddressQrCodeBodyModel(
        onBack = {},
        onRefreshClick = {},
        content =
          QrCode(
            address = address,
            addressQrImageUrl = "https://api.cash.app/qr/btc/$address?currency=btc&logoColor=000000&rounded=true&size=2000&errorCorrection=2",
            fallbackAddressQrCodeModel = QrCodeModel(data = "bitcoin:$address"),
            copyButtonIcon = Icon.SmallIconCopy,
            copyButtonLabelText = "Copy",
            onCopyClick = {},
            onShareClick = {}
          )
      )
    )
  }
}

@Preview
@Composable
fun AddressQrCodeScreenLoadingPreview() {
  PreviewWalletTheme {
    AddressQrCodeScreen(
      model = AddressQrCodeBodyModel(
        onBack = {},
        onRefreshClick = {},
        content =
          QrCode(
            address = null,
            addressQrImageUrl = null,
            fallbackAddressQrCodeModel = null,
            copyButtonIcon = Icon.SmallIconCopy,
            copyButtonLabelText = "Copy",
            onCopyClick = {},
            onShareClick = {}
          )
      )
    )
  }
}

@Preview
@Composable
fun AddressQrCodeScreenErrorPreview() {
  PreviewWalletTheme {
    AddressQrCodeScreen(
      model = AddressQrCodeBodyModel(
        onBack = {},
        onRefreshClick = {},
        content =
          Error(
            title = "We couldn’t create an address",
            subline = "We are looking into this. Please try again later."
          )
      )
    )
  }
}
