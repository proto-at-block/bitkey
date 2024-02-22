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

  test("moneyhome_screen_lite") {
    paparazzi.snapshot {
      MoneyHomeScreenLite()
    }
  }
})
