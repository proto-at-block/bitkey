package build.wallet.ui.app.settings.fingerprints

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints.ResetFingerprintsConfirmationBodyModel
import build.wallet.ui.components.screen.Screen
import io.kotest.core.spec.style.FunSpec

class ResetFingerprintsConfirmationScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("Reset Fingerprints Confirmation Screen") {
    paparazzi.snapshot {
      Screen(
        model = ScreenModel(
          body = ResetFingerprintsConfirmationBodyModel(
            onClose = {},
            onConfirmReset = {}
          )
        )
      )
    }
  }
})
