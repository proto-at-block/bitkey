package build.wallet.ui.app.qrcode

import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class BitcoinQrCodeScanScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("Bitcoin Scan Screen without camera and without address in clipboard") {
    paparazzi.snapshot {
      PreviewQrCodeScanScreenWithoutPasteAddressButton()
    }
  }

  test("Bitcoin Scan Screen without camera and with valid address in clipboard") {
    paparazzi.snapshot {
      PreviewQrCodeScanScreenWithPasteAddressButton()
    }
  }
})
