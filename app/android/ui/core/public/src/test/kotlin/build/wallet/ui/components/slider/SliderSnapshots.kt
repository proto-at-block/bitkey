package build.wallet.ui.components.slider

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class SliderSnapshots : FunSpec({
  val paparazzi = paparazziExtension(DeviceConfig.PIXEL_6)

  test("slider - 0 value") {
    paparazzi.snapshot {
      Column {
        Slider(0f)
        Slider(0f, 100f)
      }
    }
  }

  test("slider - 50% value") {
    paparazzi.snapshot {
      Column {
        Slider(.5f)
        Slider(50f, 100f)
      }
    }
  }

  test("slider - 100% value") {
    paparazzi.snapshot {
      Column {
        Slider(1f)
        Slider(100f, 100f)
      }
    }
  }
})

@Composable
private fun Slider(
  value: Float,
  sliderMax: Float = 1f,
) {
  Slider(
    value = value,
    valueRange = 0f..sliderMax,
    onValueChange = {}
  )
}
