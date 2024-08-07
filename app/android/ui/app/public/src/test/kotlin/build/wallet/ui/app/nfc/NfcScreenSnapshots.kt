package build.wallet.ui.app.nfc

import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class NfcScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension(maxPercentDifference = 0.8)

  test("NFC searching") {
    paparazzi.snapshot {
      NfcScreenSearchingPreview()
    }
  }

  test("NFC connected") {
    paparazzi.snapshot {
      NfcScreenConnectedPreview()
    }
  }

  test("NFC success") {
    paparazzi.snapshot {
      NfcScreenSuccessPreview()
    }
  }
})
