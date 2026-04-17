package build.wallet.ui.app.account.create

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.account.create.full.onboard.BuildHardwareDescriptorIntroBodyModel
import build.wallet.statemachine.account.create.full.onboard.BuildHardwareDescriptorIntroV2BodyModel
import build.wallet.ui.app.account.create.hardware.PairNewHardwareScreen
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class BuildHardwareDescriptorIntroBodyModelSnapshotTests : FunSpec({
  val paparazzi = paparazziExtension()

  test("scan to create wallet intro screen") {
    paparazzi.snapshot {
      FormScreen(
        model = BuildHardwareDescriptorIntroBodyModel(
          onTapBitkey = {},
          onBack = {}
        )
      )
    }
  }

  test("scan to create wallet intro screen - design system v2") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
      PairNewHardwareScreen(
        model =
          BuildHardwareDescriptorIntroV2BodyModel(
            onTapBitkey = {},
            onBack = {}
          )
      )
    }
  }
})
