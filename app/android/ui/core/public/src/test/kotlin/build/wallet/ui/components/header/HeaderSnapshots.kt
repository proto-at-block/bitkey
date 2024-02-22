package build.wallet.ui.components.header

import app.cash.paparazzi.DeviceConfig
import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class HeaderSnapshots : FunSpec({
  val paparazzi = paparazziExtension(DeviceConfig.PIXEL_6)

  test("header with icon headline and subline") {
    paparazzi.snapshot {
      HeaderWithIconHeadlineAndSublinePreview()
    }
  }

  test("header with icon and headline") {
    paparazzi.snapshot {
      HeaderWithIconAndHeadlinePreview()
    }
  }

  test("header with headline and subline") {
    paparazzi.snapshot {
      HeaderWithHeadlineAndSublinePreview()
    }
  }

  test("header with headline and subline centered") {
    paparazzi.snapshot {
      HeaderWithHeadlineAndSublineCenteredPreview()
    }
  }

  test("header with icon, headline and subline centered") {
    paparazzi.snapshot {
      HeaderWithIconHeadlineAndSublineCenteredPreview()
    }
  }
})
