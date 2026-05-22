package build.wallet.ui.app.moneyhome

import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class LiteMoneyHomeScreenSnapshots : FunSpec({
  // Lite-mode snapshots have a small, repeatable Linux CI rendering drift
  // beyond the shared default threshold.
  val paparazzi = paparazziExtension(maxPercentDifference = 0.05)

  test("MoneyHome Screen Lite with protecting wallets") {
    paparazzi.snapshot {
      MoneyHomeScreenLite()
    }
  }

  test("MoneyHome Screen Lite without protecting wallets") {
    paparazzi.snapshot {
      MoneyHomeScreenLiteWithoutProtectedCustomers()
    }
  }
})
