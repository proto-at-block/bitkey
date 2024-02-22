package build.wallet.ui.app.recovery

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.recovery.inprogress.RecoverYourMobileKeyBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class AppDelayAndNotifyInstructionsFormScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("recover your mobile key screen") {
    paparazzi.snapshot {
      FormScreen(
        RecoverYourMobileKeyBodyModel(
          onBack = {},
          onStartRecovery = {}
        )
      )
    }
  }
})
