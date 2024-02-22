package build.wallet.ui.app.recovery

import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.recovery.lostapp.initiate.RecoveryConflictModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class RecoveryConflictModelSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("customer canceling lost app") {
    paparazzi.snapshot {
      FormScreen(
        RecoveryConflictModel(
          cancelingRecoveryLostFactor = App,
          onCancelRecovery = {},
          presentationStyle = ScreenPresentationStyle.Modal
        ).body as FormBodyModel
      )
    }
  }

  test("customer canceling lost hardware") {
    paparazzi.snapshot {
      FormScreen(
        RecoveryConflictModel(
          cancelingRecoveryLostFactor = Hardware,
          onCancelRecovery = {},
          presentationStyle = ScreenPresentationStyle.Modal
        ).body as FormBodyModel
      )
    }
  }
})
