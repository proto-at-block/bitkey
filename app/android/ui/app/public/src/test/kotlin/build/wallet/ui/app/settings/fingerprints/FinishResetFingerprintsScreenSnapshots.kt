package build.wallet.ui.app.settings.fingerprints

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints.FinishResetFingerprintsBodyModel
import build.wallet.ui.components.screen.Screen
import io.kotest.core.spec.style.FunSpec

class FinishResetFingerprintsScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("Finish Reset Fingerprints Screen") {
    paparazzi.snapshot {
      Screen(
        model = ScreenModel(
          body = FinishResetFingerprintsBodyModel(
            onClose = {},
            onConfirmReset = {},
            onCancelReset = {}
          )
        )
      )
    }
  }
})
