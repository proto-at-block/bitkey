package build.wallet.ui.components.callout

import app.cash.paparazzi.DeviceConfig
import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class CalloutSnapshots : FunSpec({
  val paparazzi = paparazziExtension(
    deviceConfig = DeviceConfig(
      screenHeight = 1500,
      screenWidth = 2000
    )
  )

  test("callouts") {
    paparazzi.snapshot {
      CalloutList()
    }
  }
})
