package build.wallet.ui.components.icon

import app.cash.paparazzi.DeviceConfig
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.theme.WalletTheme
import io.kotest.core.spec.style.FunSpec

class IconSnapshots : FunSpec({
  val paparazzi = paparazziExtension(
    deviceConfig = DeviceConfig(
      screenHeight = 1500,
      screenWidth = 1500
    )
  )

  test("small icons") {
    paparazzi.snapshot {
      IconGrid(size = IconSize.Small)
    }
  }

  test("regular icons") {
    paparazzi.snapshot {
      IconGrid(size = IconSize.Medium)
    }
  }

  test("large icons") {
    paparazzi.snapshot {
      IconGrid(size = IconSize.Large)
    }
  }

  test("avatar icons") {
    paparazzi.snapshot {
      IconGrid(size = IconSize.Avatar)
    }
  }

  test("tinted icons") {
    paparazzi.snapshot {
      IconGrid(
        size = IconSize.Regular,
        color = WalletTheme.colors.warningForeground
      )
    }
  }
})
