package build.wallet.ui.app.settings.fingerprints

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetConfirmationBodyModel
import build.wallet.ui.components.screen.Screen
import io.kotest.core.spec.style.FunSpec

class FingerprintResetConfirmationScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("Fingerprint Reset Confirmation Screen") {
    paparazzi.snapshot {
      Screen(
        model = ScreenModel(
          body = FingerprintResetConfirmationBodyModel(
            onClose = {},
            onConfirmReset = {}
          )
        )
      )
    }
  }
})
