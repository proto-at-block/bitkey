package build.wallet.ui.app.settings.device

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.settings.full.device.wipedevice.confirmation.ScanAndWipeConfirmationSheetBodyModel
import build.wallet.ui.app.paparazzi.snapshotSheet
import io.kotest.core.spec.style.FunSpec

class WipingDeviceConfirmationSheetSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("Scan and wipe confirmation sheet model") {
    paparazzi.snapshotSheet(
      ScanAndWipeConfirmationSheetBodyModel(
        onBack = {},
        onConfirmWipeDevice = {}
      )
    )
  }
})
