package build.wallet.ui.components.fee

import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class FeeOptionSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("selected fee option") {
    paparazzi.snapshot {
      PreviewFeeOptionSelected()
    }
  }

  test("not selected fee option") {
    paparazzi.snapshot {
      PreviewFeeOptionNotSelected()
    }
  }

  test("disabled fee option") {
    paparazzi.snapshot {
      PreviewFeeOptionDisabled()
    }
  }
})
