package build.wallet.ui.app.settings.device

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.settings.full.device.wipedevice.intro.UnpairedDeviceWarningSheetBodyModel
import build.wallet.statemachine.settings.full.device.wipedevice.intro.WipingDeviceIntroBodyModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.app.paparazzi.snapshotSheet
import io.kotest.core.spec.style.FunSpec

class WipingDeviceIntroSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("wipe device intro - presented modally") {
    paparazzi.snapshot {
      FormScreen(
        WipingDeviceIntroBodyModel(
          presentedModally = true,
          onBack = {},
          onWipeDevice = {}
        )
      )
    }
  }

  test("wipe device intro - presented as root screen") {
    paparazzi.snapshot {
      FormScreen(
        WipingDeviceIntroBodyModel(
          presentedModally = false,
          onBack = {},
          onWipeDevice = {}
        )
      )
    }
  }

  test("unpaired device warning sheet") {
    paparazzi.snapshotSheet(
      UnpairedDeviceWarningSheetBodyModel(
        subline = "This device might be protecting funds. If you wipe the device, the funds may no longer be accessible.",
        onWipeDevice = {},
        onCancel = {}
      )
    )
  }
})
