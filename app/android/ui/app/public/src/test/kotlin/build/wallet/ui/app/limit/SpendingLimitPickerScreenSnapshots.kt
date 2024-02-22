package build.wallet.ui.app.limit

import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class SpendingLimitPickerScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("spending limit picker screen without value") {
    paparazzi.snapshot {
      PreviewSpendingLimitPickerScreenNoValue()
    }
  }

  test("spending limit picker screen with values") {
    paparazzi.snapshot {
      PreviewSpendingLimitPickerScreenWithValue()
    }
  }
})
