package build.wallet.ui.components.banner

import app.cash.paparazzi.DeviceConfig
import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class BannerSnapshots : FunSpec({
  val paparazzi = paparazziExtension(DeviceConfig.PIXEL_6)

  test("all banners") {
    paparazzi.snapshot {
      AllBannersPreview()
    }
  }
})
