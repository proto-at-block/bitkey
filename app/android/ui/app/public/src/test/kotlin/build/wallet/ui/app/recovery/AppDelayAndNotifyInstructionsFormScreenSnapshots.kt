package build.wallet.ui.app.recovery

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.recovery.inprogress.RecoverYourAppKeyBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class AppDelayAndNotifyInstructionsFormScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("recover your App Key screen") {
    paparazzi.snapshot {
      FormScreen(
        RecoverYourAppKeyBodyModel(
          onBack = {},
          onStartRecovery = {}
        )
      )
    }
  }
})
