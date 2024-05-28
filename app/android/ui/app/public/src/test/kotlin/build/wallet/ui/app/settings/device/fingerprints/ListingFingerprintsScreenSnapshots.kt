package build.wallet.ui.app.settings.device.fingerprints

import build.wallet.firmware.EnrolledFingerprints
import build.wallet.firmware.FingerprintHandle
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.settings.full.device.fingerprints.ListingFingerprintsBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class ListingFingerprintsScreenSnapshots : FunSpec({

  val paparazzi = paparazziExtension()

  test("listing fingerprints") {
    val enrolledFingerprints = EnrolledFingerprints(
      maxCount = 3,
      fingerprintHandles = listOf(
        FingerprintHandle(index = 0, label = "Left Thumb"),
        FingerprintHandle(index = 1, label = "Right Thumb")
      )
    )
    paparazzi.snapshot {
      FormScreen(
        model =
          ListingFingerprintsBodyModel(
            enrolledFingerprints = enrolledFingerprints,
            onBack = {},
            onAddFingerprint = {},
            onEditFingerprint = {}
          )
      )
    }
  }
})
