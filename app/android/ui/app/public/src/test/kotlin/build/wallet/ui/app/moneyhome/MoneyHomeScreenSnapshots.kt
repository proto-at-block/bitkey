package build.wallet.ui.app.moneyhome

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.ui.theme.Theme
import io.kotest.core.spec.style.FunSpec

class MoneyHomeScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("MoneyHome Screen Full") {
    paparazzi.snapshot {
      MoneyHomeScreenFull()
    }
  }

  test("MoneyHome Screen Full and pending activity") {
    paparazzi.snapshot {
      MoneyHomeScreenFullWithPendingActivity()
    }
  }

  test("MoneyHome Screen Full and late pending activity") {
    paparazzi.snapshot(
      onlyTheme = Theme.DARK
    ) {
      MoneyHomeScreenFullWithLatePendingActivity()
    }
  }

  test("MoneyHome Screen Full and late pending activity light") {
    paparazzi.snapshot(
      onlyTheme = Theme.LIGHT
    ) {
      MoneyHomeScreenFullWithLatePendingActivity()
    }
  }

  test("MoneyHome Screen Full with Buy and Sell enabled") {
    paparazzi.snapshot {
      MoneyHomeScreenFullWithBuyAndSellEnabled()
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

  test("MoneyHome Screen Full loading") {
    paparazzi.snapshot {
      MoneyHomeScreenFull(isLoading = true)
    }
  }

  test("MoneyHome Screen Full with skeleton transactions") {
    paparazzi.snapshot {
      MoneyHomeScreenFull(useSkeletonTransactions = true)
    }
  }

  test("MoneyHome Screen Full with security dot hidden (no recommendations)") {
    paparazzi.snapshot {
      MoneyHomeScreenFull(
        securityHubBadged = false
      )
    }
  }

  test("MoneyHome Screen Full with security dot") {
    paparazzi.snapshot {
      MoneyHomeScreenFull(
        securityHubBadged = true
      )
    }
  }

  test("MoneyHome Screen Full new wallet getting started no activity") {
    paparazzi.snapshot {
      MoneyHomeScreenFullNewWalletGettingStartedNoActivity()
    }
  }
})
