package build.wallet.ui.app.fwup

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.fwup.FwupNextComponentReadyModel
import build.wallet.ui.model.render
import io.kotest.core.spec.style.FunSpec

class FwupNextComponentReadySnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("fwup next component ready dsv2") {
    paparazzi.snapshot {
      FwupNextComponentReadyModel(
        completedIndex = 1,
        totalMcus = 2,
        onBack = {},
        onContinue = {}
      ).render()
    }
  }
})
