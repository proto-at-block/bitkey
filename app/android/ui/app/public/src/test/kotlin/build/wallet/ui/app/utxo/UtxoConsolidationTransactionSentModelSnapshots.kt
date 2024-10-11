package build.wallet.ui.app.utxo

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.utxo.UtxoConsolidationTransactionSentModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class UtxoConsolidationTransactionSentModelSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("confirmation screen") {
    paparazzi.snapshot {
      FormScreen(
        UtxoConsolidationTransactionSentModel(
          targetAddress = "bc1q xy2k gdyg jrsq tzq2 n0yr f249 3p83 kkfj hx0w lh",
          arrivalTime = "Aug 7, 12:24 pm",
          utxosCountConsolidated = "20 → 1",
          consolidationCostBitcoin = "65,509 sats",
          consolidationCostFiat = "$37.42",
          onBack = {},
          onDone = {}
        )
      )
    }
  }
})
