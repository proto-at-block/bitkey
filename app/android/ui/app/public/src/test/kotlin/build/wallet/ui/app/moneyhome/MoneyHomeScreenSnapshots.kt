package build.wallet.ui.app.moneyhome

import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class MoneyHomeScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("moneyhome_screen_full") {
    paparazzi.snapshot {
      MoneyHomeScreenFull()
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
