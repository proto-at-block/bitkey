package build.wallet.ui.app.moneyhome.receive

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.partnerships.PartnerId
import build.wallet.partnerships.PartnerInfo
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.qr.QRMatrix
import build.wallet.statemachine.qr.QrCodeState
import build.wallet.statemachine.receive.AddressQrCodeBodyModel
import build.wallet.ui.tooling.PreviewWalletTheme
import kotlinx.collections.immutable.toImmutableList

@Preview
@Composable
fun AddressQrCodeScreenPreview() {
  val address = "bc1q42lja79elem0anu8q8s3h2n687re9jax556pcc"
  PreviewWalletTheme {
    AddressQrCodeScreen(
      model = AddressQrCodeBodyModel(
        onBack = {},
        onRefreshClick = {},
        content = AddressQrCodeBodyModel.Content.QrCode(
          address = address,
          qrCodeState = QrCodeState.Success(
            matrix = QRMatrix(
              columnWidth = 45,
              data = BooleanArray(2026) { it % 4 == 0 }
            )
          ),
          copyButtonIcon = Icon.SmallIconCopy,
          copyButtonLabelText = "Copy",
          onCopyClick = {},
          onPartnerClick = {},
          onShareClick = {}
        )
      )
    )
  }
}

@Preview
@Composable
fun AddressQrCodeScreenPreviewWithPartner() {
  val address = "bc1q42lja79elem0anu8q8s3h2n687re9jax556pcc"
  PreviewWalletTheme {
    AddressQrCodeScreen(
      model = AddressQrCodeBodyModel(
        onBack = {},
        onRefreshClick = {},
        content = AddressQrCodeBodyModel.Content.QrCode(
          address = address,
          qrCodeState = QrCodeState.Success(
            matrix = QRMatrix(
              columnWidth = 45,
              data = BooleanArray(2026) { it % 4 == 0 }
            )
          ),
          copyButtonIcon = Icon.SmallIconCopy,
          copyButtonLabelText = "Copy",
          onCopyClick = {},
          onShareClick = {},
          onPartnerClick = {},
          partners = listOf(
            PartnerInfo(
              null,
              null,
              "Robinhood",
              PartnerId("Robinhood")
            ),
          ).toImmutableList()
        )
      )
    )
  }
}

@Preview
@Composable
fun AddressQrCodeScreenPreviewWithPartners() {
  val address = "bc1q42lja79elem0anu8q8s3h2n687re9jax556pcc"
  PreviewWalletTheme {
    AddressQrCodeScreen(
      model = AddressQrCodeBodyModel(
        onBack = {},
        onRefreshClick = {},
        content = AddressQrCodeBodyModel.Content.QrCode(
          address = address,
          qrCodeState = QrCodeState.Success(
            matrix = QRMatrix(
              columnWidth = 45,
              data = BooleanArray(2026) { it % 4 == 0 }
            )
          ),
          copyButtonIcon = Icon.SmallIconCopy,
          copyButtonLabelText = "Copy",
          onCopyClick = {},
          onShareClick = {},
          onPartnerClick = {},
          partners = listOf(
            PartnerInfo(
              null,
              null,
              "Robinhood",
              PartnerId("Robinhood")
            ),
            PartnerInfo(
              null,
              null,
              "Moonpay",
              PartnerId("Moonpay")
            ),
            PartnerInfo(
              null,
              null,
              "Cash App",
              PartnerId("Cash App")
            )
          ).toImmutableList()
        )
      )
    )
  }
}

@Preview
@Composable
fun AddressQrCodeScreenQrCodeErrorPreview() {
  val address = "bc1q42lja79elem0anu8q8s3h2n687re9jax556pcc"
  PreviewWalletTheme {
    AddressQrCodeScreen(
      model = AddressQrCodeBodyModel(
        onBack = {},
        onRefreshClick = {},
        content =
          AddressQrCodeBodyModel.Content.QrCode(
            address = address,
            qrCodeState = QrCodeState.Error,
            copyButtonIcon = Icon.SmallIconCopy,
            copyButtonLabelText = "Copy",
            onCopyClick = {},
            onPartnerClick = {},
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
          AddressQrCodeBodyModel.Content.QrCode(
            address = null,
            qrCodeState = QrCodeState.Loading,
            copyButtonIcon = Icon.SmallIconCopy,
            copyButtonLabelText = "Copy",
            onCopyClick = {},
            onPartnerClick = {},
            onShareClick = {}
          )
      )
    )
  }
}

@Preview
@Composable
fun AddressQrCodeScreenAddressErrorPreview() {
  PreviewWalletTheme {
    AddressQrCodeScreen(
      model = AddressQrCodeBodyModel(
        onBack = {},
        onRefreshClick = {},
        content =
          AddressQrCodeBodyModel.Content.Error(
            title = "We couldn’t create an address",
            subline = "We are looking into this. Please try again later."
          )
      )
    )
  }
}
