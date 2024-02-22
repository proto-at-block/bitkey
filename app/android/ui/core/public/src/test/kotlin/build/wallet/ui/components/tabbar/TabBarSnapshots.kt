package build.wallet.ui.components.tabbar

import app.cash.paparazzi.DeviceConfig
import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class TabBarSnapshots : FunSpec({
  val paparazzi = paparazziExtension(DeviceConfig.PIXEL_6)

  test("tab bar with 2 items") {
    paparazzi.snapshot {
      TabBarTwoItemsPreview()
    }
  }

  test("tab bar with 3 items") {
    paparazzi.snapshot {
      TabBarThreeItemsPreview()
    }
  }

  test("tab bar with 4 items") {
    paparazzi.snapshot {
      TabBarFourItemsPreview()
    }
  }
})
