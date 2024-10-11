package build.wallet.ui.app.utxo

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.utxo.ExceedsMaxUtxoCountBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class ExceedsMaxUtxoCountBodyModelSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("exceeds max utxo count info screen") {
    paparazzi.snapshot {
      FormScreen(
        ExceedsMaxUtxoCountBodyModel(
          onBack = {},
          maxUtxoCount = 50,
          onContinue = {}
        )
      )
    }
  }
})
