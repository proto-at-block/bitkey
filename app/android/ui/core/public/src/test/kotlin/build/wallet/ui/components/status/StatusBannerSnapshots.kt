package build.wallet.ui.components.status

import app.cash.paparazzi.DeviceConfig
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.ui.model.status.BannerStyle
import build.wallet.ui.model.status.StatusBannerModel
import io.kotest.core.spec.style.FunSpec

class StatusBannerSnapshots : FunSpec({
  val paparazzi = paparazziExtension(DeviceConfig.PIXEL_6)

  test("warning status banner with design system v2 feature flag off") {
    paparazzi.snapshot {
      StatusBanner(model = warningStatusBannerModel())
    }
  }

  test("destructive status banner with design system v2 feature flag off") {
    paparazzi.snapshot {
      StatusBanner(model = destructiveStatusBannerModel())
    }
  }

  test("warning status banner with design system v2 feature flag on") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
      StatusBanner(model = warningStatusBannerModel())
    }
  }

  test("destructive status banner with design system v2 feature flag on") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
      StatusBanner(model = destructiveStatusBannerModel())
    }
  }
})

private fun warningStatusBannerModel() =
  StatusBannerModel(
    title = "Offline",
    subtitle = "Balance last updated at 9:43pm",
    style = BannerStyle.Warning
  ) {}

private fun destructiveStatusBannerModel() =
  StatusBannerModel(
    title = "Title",
    subtitle = "Subtitle",
    style = BannerStyle.Destructive
  ) {}
