package build.wallet.ui.app.transactions

import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class BitcoinTransactionSpeedUpEducationSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("speed up transaction education dsv2") {
    paparazzi.snapshot {
      BitcoinTransactionSpeedUpEducationDesignSystemV2Preview()
    }
  }
})
