package build.wallet.ui.components.screen

import app.cash.paparazzi.DeviceConfig
import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class ScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension(DeviceConfig.PIXEL_6)

  test("screen with body only") {
    paparazzi.snapshot {
      ScreenWithBodyOnlyPreview()
    }
  }

  test("screen with alert") {
    paparazzi.snapshot {
      ScreenWithBodyAndAlertPreview()
    }
  }

  test("screen with status banner") {
    paparazzi.snapshot {
      ScreenWithBodyAndStatusBannerPreview()
    }
  }
})
