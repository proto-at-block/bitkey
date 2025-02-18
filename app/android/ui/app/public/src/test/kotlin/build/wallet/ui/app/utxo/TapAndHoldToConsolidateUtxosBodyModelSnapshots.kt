package build.wallet.ui.app.utxo

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.utxo.TapAndHoldToConsolidateUtxosBodyModel
import build.wallet.ui.app.paparazzi.snapshotSheet
import io.kotest.core.spec.style.FunSpec

class TapAndHoldToConsolidateUtxosBodyModelSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("tap and hold to consolidate utxos half sheet") {
    paparazzi.snapshotSheet(
      TapAndHoldToConsolidateUtxosBodyModel(
        onBack = {},
        onConsolidate = {}
      )
    )
  }
})
