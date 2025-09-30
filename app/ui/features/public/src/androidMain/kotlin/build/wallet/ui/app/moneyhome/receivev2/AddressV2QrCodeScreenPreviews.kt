package build.wallet.ui.app.moneyhome.receivev2

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.qr.QRMatrix
import build.wallet.statemachine.qr.QrCodeState
import build.wallet.statemachine.receivev2.AddressQrCodeV2BodyModel
import build.wallet.statemachine.receivev2.AddressQrCodeV2BodyModel.Content.Error
import build.wallet.statemachine.receivev2.AddressQrCodeV2BodyModel.Content.QrCode
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun AddressQrCodeV2ScreenPreview() {
  val address = "bc1q42lja79elem0anu8q8s3h2n687re9jax556pcc"
  PreviewWalletTheme {
    AddressQrCodeV2Screen(
      model = AddressQrCodeV2BodyModel(
        onBack = {},
        onRefreshClick = {},
        content = QrCode(
          address = address,
          qrCodeState = QrCodeState.Success(
            matrix = QRMatrix(
              columnWidth = 45,
              data = mutableListOf<Boolean>().apply {
                for (i in 0..2025) {
                  add(i % 4 == 0)
                }
              }.toBooleanArray()
            )
          ),
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
fun AddressQrCodeV2ScreenQrCodeErrorPreview() {
  val address = "bc1q42lja79elem0anu8q8s3h2n687re9jax556pcc"
  PreviewWalletTheme {
    AddressQrCodeV2Screen(
      model = AddressQrCodeV2BodyModel(
        onBack = {},
        onRefreshClick = {},
        content =
          QrCode(
            address = address,
            qrCodeState = QrCodeState.Error,
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
fun AddressQrCodeV2ScreenLoadingPreview() {
  PreviewWalletTheme {
    AddressQrCodeV2Screen(
      model = AddressQrCodeV2BodyModel(
        onBack = {},
        onRefreshClick = {},
        content =
          QrCode(
            address = null,
            qrCodeState = QrCodeState.Loading,
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
fun AddressQrCodeV2ScreenAddressErrorPreview() {
  PreviewWalletTheme {
    AddressQrCodeV2Screen(
      model = AddressQrCodeV2BodyModel(
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
