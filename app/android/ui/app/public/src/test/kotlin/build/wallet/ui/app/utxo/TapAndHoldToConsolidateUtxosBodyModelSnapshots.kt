package build.wallet.ui.app.utxo

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.utxo.TapAndHoldToConsolidateUtxosBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class TapAndHoldToConsolidateUtxosBodyModelSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("tap and hold to consolidate utxos half sheet") {
    paparazzi.snapshot {
      FormScreen(
        TapAndHoldToConsolidateUtxosBodyModel(
          onBack = {},
          onConsolidate = {}
        )
      )
    }
  }
})
