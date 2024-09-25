package build.wallet.ui.app.moneyhome

import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class MoneyHomeScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("MoneyHome Screen Full") {
    paparazzi.snapshot {
      MoneyHomeScreenFull()
    }
  }

  test("MoneyHome Screen Full with large balance") {
    paparazzi.snapshot {
      MoneyHomeScreenFull(largeBalance = true)
    }
  }

  test("MoneyHome Screen Full with hidden balance") {
    paparazzi.snapshot {
      MoneyHomeScreenFull(hideBalance = true)
    }
  }

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
