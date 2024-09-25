package build.wallet.ui.app.utxo

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.utxo.utxoConsolidationConfirmationModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class UtxoConsolidationConfirmationModelSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("confirmation screen") {
    paparazzi.snapshot {
      FormScreen(
        utxoConsolidationConfirmationModel(
          balanceFiat = "$15,000",
          balanceBitcoin = "26,259,461 sats",
          utxoCount = "20",
          consolidationCostFiat = "$37.42",
          consolidationCostBitcoin = "65,000 sats",
          onBack = {},
          onConfirmClick = {},
          onConsolidationTimeClick = {},
          onConsolidationCostClick = {}
        )
      )
    }
  }
})
