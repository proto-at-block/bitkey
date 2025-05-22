package build.wallet.ui.components.status

import app.cash.paparazzi.DeviceConfig
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.ui.model.status.BannerStyle
import build.wallet.ui.model.status.StatusBannerModel
import io.kotest.core.spec.style.FunSpec

class StatusBannerSnapshots : FunSpec({
  val paparazzi = paparazziExtension(DeviceConfig.PIXEL_6)

  test("warning status banner") {
    paparazzi.snapshot {
      StatusBanner(
        model =
          StatusBannerModel(
            title = "Offline",
            subtitle = "Balance last updated at 9:43pm",
            style = BannerStyle.Warning
          ) {}
      )
    }
  }

  test("destructive status banner") {
    paparazzi.snapshot {
      StatusBanner(
        model =
          StatusBannerModel(
            title = "Title",
            subtitle = "Subtitle",
            style = BannerStyle.Destructive
          ) {}
      )
    }
  }
})
