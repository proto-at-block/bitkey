package build.wallet.ui.app.recovery

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.recovery.hardware.initiating.HardwareReplacementInstructionsModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class HardwareReplacementInstructionsFormScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("hardware replacement instructions screen") {
    paparazzi.snapshot {
      FormScreen(
        model =
          HardwareReplacementInstructionsModel(
            onClose = {},
            onContinue = {}
          )
      )
    }
  }
})
