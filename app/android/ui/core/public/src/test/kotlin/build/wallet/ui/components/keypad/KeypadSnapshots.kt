package build.wallet.ui.components.keypad

import app.cash.paparazzi.DeviceConfig
import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class KeypadSnapshots : FunSpec({
  val paparazzi = paparazziExtension(DeviceConfig.PIXEL_6)

  test("keypad with decimal button") {
    paparazzi.snapshot {
      KeypadWithDecimalPreview()
    }
  }

  test("keypad without decimal button") {
    paparazzi.snapshot {
      KeypadNoDecimalPreview()
    }
  }
})
