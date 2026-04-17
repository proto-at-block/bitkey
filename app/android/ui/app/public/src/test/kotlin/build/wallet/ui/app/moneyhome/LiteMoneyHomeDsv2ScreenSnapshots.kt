package build.wallet.ui.app.moneyhome

import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class LiteMoneyHomeDsv2ScreenSnapshots : FunSpec({
  // DSv2 lite-mode snapshots have a small, repeatable Linux CI rendering drift
  // beyond the shared default threshold.
  val paparazzi = paparazziExtension(maxPercentDifference = 0.05)

  test("MoneyHome Screen Lite with protecting wallets with design system v2") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
      MoneyHomeScreenLite(isDesignSystemV2Enabled = true)
    }
  }

  test("MoneyHome Screen Lite without protecting wallets with design system v2") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
      MoneyHomeScreenLiteWithoutProtectedCustomers(isDesignSystemV2Enabled = true)
    }
  }
})
