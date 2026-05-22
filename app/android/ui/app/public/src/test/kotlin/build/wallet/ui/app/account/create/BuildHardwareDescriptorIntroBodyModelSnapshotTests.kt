package build.wallet.ui.app.account.create

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.account.create.full.onboard.BuildHardwareDescriptorIntroBodyModel
import build.wallet.ui.app.account.create.hardware.PairNewHardwareScreen
import io.kotest.core.spec.style.FunSpec

class BuildHardwareDescriptorIntroBodyModelSnapshotTests : FunSpec({
  val paparazzi = paparazziExtension()

  test("scan to create wallet intro screen") {
    paparazzi.snapshot {
      PairNewHardwareScreen(
        model =
          BuildHardwareDescriptorIntroBodyModel(
            onTapBitkey = {},
            onBack = {}
          )
      )
    }
  }
})
