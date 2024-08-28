package build.wallet.ui.app.fwup

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.fwup.FwupUpdateDeviceModel
import build.wallet.statemachine.nfc.FwupInstructionsBodyModel
import build.wallet.ui.app.nfc.FwupInstructionsScreen
import io.kotest.core.spec.style.FunSpec

class FwupUpdateDeviceSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("fwup update device screen") {
    paparazzi.snapshot {
      FwupInstructionsScreen(
        model =
          FwupUpdateDeviceModel(
            onClose = {},
            onLaunchFwup = {},
            onReleaseNotes = {},
            bottomSheetModel = null
          ).body as FwupInstructionsBodyModel
      )
    }
  }
})
