package build.wallet.ui.components.tabbar

import app.cash.paparazzi.DeviceConfig
import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class TabBarSnapshots : FunSpec({
  val paparazzi = paparazziExtension(DeviceConfig.PIXEL_6)

  test("tab bar with home and security hub") {
    paparazzi.snapshot {
      TabBarWithHomeAndSecurityHub()
    }
  }

  test("tab bar with home and security hub badged") {
    paparazzi.snapshot {
      TabBarWithHomeAndSecurityHubBadged()
    }
  }
})
