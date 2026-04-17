package build.wallet.ui.app.limit

import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class SpendingLimitPickerScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("spending limit picker screen without value using keypad") {
    paparazzi.snapshot {
      PreviewSpendingLimitPickerScreenNoValueKeypad()
    }
  }

  test("spending limit picker screen with values using keypad") {
    paparazzi.snapshot {
      PreviewSpendingLimitPickerScreenWithValueKeypad()
    }
  }

  test("spending limit picker screen with values using keypad and design system v2") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
      PreviewSpendingLimitPickerScreenWithValueKeypadDesignSystemV2()
    }
  }
})
