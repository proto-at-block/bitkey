package build.wallet.ui.app.receive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.qr.QrCodeModel
import build.wallet.statemachine.receive.AddressQrCodeBodyModel
import build.wallet.statemachine.receive.AddressQrCodeBodyModel.Content.Error
import build.wallet.statemachine.receive.AddressQrCodeBodyModel.Content.QrCode
import build.wallet.ui.app.moneyhome.receive.AddressQrCodeScreen
import build.wallet.ui.theme.WalletTheme
import io.kotest.core.spec.style.FunSpec

class AddressQrCodeScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("qr code") {
    paparazzi.snapshot {
      Box(modifier = Modifier.background(WalletTheme.colors.background)) {
        build.wallet.ui.components.qr.QrCode(
          modifier = Modifier.size(300.1234f.dp),
          data = "Hello World!".repeat(10)
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

  test("qr code screen loading") {
    paparazzi.snapshot {
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

  test("qr code screen with error") {
    paparazzi.snapshot {
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
})
