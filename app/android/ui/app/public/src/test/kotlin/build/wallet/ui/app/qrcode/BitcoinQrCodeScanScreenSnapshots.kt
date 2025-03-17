package build.wallet.ui.app.qrcode

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.send.BitcoinQrCodeScanBodyModel
import io.kotest.core.spec.style.FunSpec

class BitcoinQrCodeScanScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("Bitcoin Scan Screen without camera and without address in clipboard") {
    paparazzi.snapshot {
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

  test("Bitcoin Scan Screen without camera and with valid address in clipboard") {
    paparazzi.snapshot {
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
})
