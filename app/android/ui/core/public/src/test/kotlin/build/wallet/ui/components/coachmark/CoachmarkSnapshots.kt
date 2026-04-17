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

  test("private wallet home coachmark with design system v2 feature flag on") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
      PrivateWalletHomeCoachmarkPreview()
    }
  }

  test("bip177 coachmark") {
    paparazzi.snapshot {
      Bip177CoachmarkPreview()
    }
  }

  test("bip177 coachmark with design system v2 feature flag on") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
      Bip177CoachmarkPreview()
    }
  }

  test("W3 upgrade complete coachmark") {
    paparazzi.snapshot {
      W3UpgradeCompleteCoachmarkPreview()
    }
  }

  test("W3 upgrade complete coachmark with design system v2 feature flag on") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
      W3UpgradeCompleteCoachmarkPreview()
    }
  }
})
