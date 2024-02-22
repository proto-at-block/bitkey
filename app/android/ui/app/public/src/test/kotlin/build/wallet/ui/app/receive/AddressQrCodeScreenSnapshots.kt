package build.wallet.ui.app.receive

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.ui.app.moneyhome.receive.AddressQrCodeScreenErrorPreview
import build.wallet.ui.app.moneyhome.receive.AddressQrCodeScreenPreview
import io.kotest.core.spec.style.FunSpec

class AddressQrCodeScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("qr code screen") {
    paparazzi.snapshot {
      AddressQrCodeScreenPreview()
    }
  }

  test("qr code screen with error") {
    paparazzi.snapshot {
      AddressQrCodeScreenErrorPreview()
    }
  }
})
