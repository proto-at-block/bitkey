package build.wallet.ui.app.receive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.partnerships.PartnerId
import build.wallet.partnerships.PartnerInfo
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.qr.QrCodeModel
import build.wallet.statemachine.qr.QrCodeState
import build.wallet.statemachine.qr.toQrMatrix
import build.wallet.statemachine.receive.AddressQrCodeBodyModel
import build.wallet.ui.app.moneyhome.receive.AddressQrCodeScreen
import build.wallet.ui.components.qr.QrCode
import build.wallet.ui.theme.WalletTheme
import io.kotest.core.spec.style.FunSpec
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.runBlocking

class AddressQrCodeScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("qr code") {
    paparazzi.snapshot {
      Box(modifier = Modifier.background(WalletTheme.colors.background)) {
        QrCode(
          matrix = runBlocking {
            "Hello World!".repeat(10).toQrMatrix().value
          }
        )
      }
    }
  }

  test("qr code screen") {
    paparazzi.snapshot {
      val address = "bc1q42lja79elem0anu8q8s3h2n687re9jax556pcc"
      AddressQrCodeScreen(
        model = AddressQrCodeBodyModel(
          onBack = {},
          onRefreshClick = {},
          content =
            AddressQrCodeBodyModel.Content.QrCode(
              address = address,
              qrCodeState = QrCodeState.Success(
                runBlocking {
                  QrCodeModel(data = "bitcoin:$address").data.toQrMatrix().value
                }
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

  test("qr code screen with partners") {
    paparazzi.snapshot {
      val address = "bc1q42lja79elem0anu8q8s3h2n687re9jax556pcc"
      AddressQrCodeScreen(
        model = AddressQrCodeBodyModel(
          onBack = {},
          onRefreshClick = {},
          content =
            AddressQrCodeBodyModel.Content.QrCode(
              address = address,
              qrCodeState = QrCodeState.Success(
                runBlocking {
                  QrCodeModel(data = "bitcoin:$address").data.toQrMatrix().value
                }
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

  test("qr code screen loading") {
    paparazzi.snapshot {
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

  test("qr code screen with error") {
    paparazzi.snapshot {
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

  test("qr code screen with qr error") {
    paparazzi.snapshot {
      val address = "bc1q42lja79elem0anu8q8s3h2n687re9jax556pcc"
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
})
