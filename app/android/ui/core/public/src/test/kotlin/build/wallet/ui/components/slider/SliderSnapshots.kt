package build.wallet.ui.components.slider

import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class SliderSnapshots : FunSpec({
  val paparazzi = paparazziExtension(DeviceConfig.PIXEL_6)

  test("slider - 0 value") {
    paparazzi.snapshot {
      Slider(0f)
    }
  }

  test("slider - 50% value") {
    paparazzi.snapshot {
      Slider(.5f)
    }
  }

  test("slider - 100% value") {
    paparazzi.snapshot {
      Slider(1f)
    }
  }
})

@Composable
private fun Slider(value: Float) {
  Slider(value = value, onValueChange = {})
}
