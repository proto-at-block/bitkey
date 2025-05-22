package build.wallet.ui.app.settings.fingerprints

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints.ResetFingerprintsConfirmationSheetModel
import build.wallet.ui.app.paparazzi.snapshotSheet
import io.kotest.core.spec.style.FunSpec

class ResetFingerprintsConfirmationSheetSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("Reset fingerprints confirmation sheet") {
    paparazzi.snapshotSheet(
      ResetFingerprintsConfirmationSheetModel(
        onDismiss = {},
        onConfirmReset = {}
      )
    )
  }
})
