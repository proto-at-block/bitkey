package build.wallet.ui.components.coachmark

import app.cash.paparazzi.DeviceConfig
import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class CoachmarkSnapshots : FunSpec({
  val paparazzi = paparazziExtension(DeviceConfig.PIXEL_6)

  test("private wallet home coachmark") {
    paparazzi.snapshot {
      PrivateWalletHomeCoachmarkPreview()
    }
  }

  test("bip177 coachmark") {
    paparazzi.snapshot {
      Bip177CoachmarkPreview()
    }
  }
})
