package build.wallet.ui.components.limit

import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class SpendingLimitCardSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("Spending Limit Card") {
    paparazzi.snapshot {
      PreviewSpendingLimitCard()
    }
  }
})
