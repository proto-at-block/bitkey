package build.wallet.ui.app.utxo

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.utxo.UtxoConsolidationSpeedUpTransactionSentModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class UtxoConsolidationSpeedUpTransactionSentModelSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("consolidation speed up sent") {
    paparazzi.snapshot {
      FormScreen(
        UtxoConsolidationSpeedUpTransactionSentModel(
          targetAddress = "bc1q xy2k gdyg jrsq tzq2 n0yr f249 3p83 kkfj hx0w lh",
          arrivalTime = "~10 minutes",
          originalConsolidationCost = "$37.42",
          originalConsolidationCostSecondaryText = "58,761 sats",
          consolidationCostDifference = "+$2.48",
          consolidationCostDifferenceSecondaryText = "3,849 sats",
          totalConsolidationCost = "$39.90",
          totalConsolidationCostSecondaryText = "62,610 sats",
          onBack = {},
          onDone = {}
        )
      )
    }
  }
})
