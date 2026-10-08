package build.wallet.ui.app.account.create

import app.cash.paparazzi.DeviceConfig
import build.wallet.kotest.paparazzi.IPHONE_DUO_UNFOLDED
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

  test("scan to create wallet intro screen - compact height") {
    // Unfolded iPhone Duo in landscape: short viewport where hero content overflows and must
    // scroll while the footer button stays anchored.
    paparazzi.snapshot(deviceConfig = DeviceConfig.IPHONE_DUO_UNFOLDED) {
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
