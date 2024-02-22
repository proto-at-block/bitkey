package build.wallet.ui.app.partnerships

import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class CustomAmountScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("custom amount entry screen - invalid entry") {
    paparazzi.snapshot {
      CustomAmountScreenInvalidEntryPreview()
    }
  }

  test("custom amount entry screen - valid entry") {
    paparazzi.snapshot {
      CustomAmountScreenValidEntryPreview()
    }
  }
})
