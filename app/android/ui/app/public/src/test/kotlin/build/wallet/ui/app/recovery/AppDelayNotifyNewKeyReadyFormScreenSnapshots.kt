package build.wallet.ui.app.recovery

import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.recovery.inprogress.DelayAndNotifyNewKeyReady
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class AppDelayNotifyNewKeyReadyFormScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("new key ready screen") {
    paparazzi.snapshot {
      FormScreen(
        DelayAndNotifyNewKeyReady(
          factorToRecover = App,
          onCompleteRecovery = {},
          onStopRecovery = {},
          onExit = null
        )
      )
    }
  }
})
