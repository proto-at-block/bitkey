package build.wallet.ui.app.settings.device.fingerprints

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.settings.full.device.fingerprints.ManageFingerprintsOptionsSheetModel
import build.wallet.ui.app.paparazzi.snapshotSheet
import io.kotest.core.spec.style.FunSpec

class ManageFingerprintsOptionsSheetSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("manage fingerprints options sheet") {
    paparazzi.snapshotSheet(
      ManageFingerprintsOptionsSheetModel(
        fingerprintResetEnabled = true,
        canEditFingerprints = true,
        onDismiss = {},
        onEditFingerprints = {},
        onCannotUnlock = {}
      )
    )
  }
})
