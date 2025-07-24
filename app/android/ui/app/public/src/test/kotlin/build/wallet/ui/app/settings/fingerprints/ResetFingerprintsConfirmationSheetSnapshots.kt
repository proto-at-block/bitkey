package build.wallet.ui.app.settings.fingerprints

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetConfirmationSheetModel
import build.wallet.ui.app.paparazzi.snapshotSheet
import io.kotest.core.spec.style.FunSpec

class FingerprintResetConfirmationSheetSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("Fingerprint reset confirmation sheet") {
    paparazzi.snapshotSheet(
      FingerprintResetConfirmationSheetModel(
        onDismiss = {},
        onConfirmReset = {}
      )
    )
  }
})
