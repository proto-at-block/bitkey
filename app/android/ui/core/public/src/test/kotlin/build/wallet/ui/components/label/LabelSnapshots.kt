package build.wallet.ui.components.label

import app.cash.paparazzi.DeviceConfig
import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class LabelSnapshots : FunSpec({
  val paparazzi = paparazziExtension(DeviceConfig.PIXEL_6)

  test("all labels") {
    paparazzi.snapshot {
      AllLabelsPreview()
    }
  }

  test("label with long content") {
    paparazzi.snapshot {
      LabelWithLongContentPreview()
    }
  }
})
