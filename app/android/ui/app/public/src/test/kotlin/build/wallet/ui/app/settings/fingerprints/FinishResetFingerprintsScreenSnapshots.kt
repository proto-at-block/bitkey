package build.wallet.ui.app.settings.fingerprints

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FinishFingerprintResetBodyModel
import build.wallet.ui.components.screen.Screen
import io.kotest.core.spec.style.FunSpec

class FinishFingerprintResetScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("Finish Fingerprint Reset Screen") {
    paparazzi.snapshot {
      Screen(
        model = ScreenModel(
          body = FinishFingerprintResetBodyModel(
            onClose = {},
            onConfirmReset = {},
            onCancelReset = {}
          )
        )
      )
    }
  }
})
