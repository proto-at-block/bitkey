package build.wallet.ui.app.settings.fingerprints

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetEnrollmentFailureBodyModel
import build.wallet.ui.components.screen.Screen
import io.kotest.core.spec.style.FunSpec

class FingerprintEnrollmentFailureScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("Fingerprint Enrollment Failure Screen") {
    paparazzi.snapshot {
      Screen(
        model = ScreenModel(
          body = FingerprintResetEnrollmentFailureBodyModel(
            onBackClick = {},
            onTryAgain = {}
          )
        )
      )
    }
  }
})
