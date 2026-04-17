package build.wallet.ui.components.label

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class LabelSnapshots : FunSpec({
  val paparazzi = paparazziExtension(DeviceConfig.PIXEL_6)

  test("label with long content") {
    paparazzi.snapshot {
      LabelWithLongContentPreview()
    }
  }
})

class AllLabelSnapshots : FunSpec({
  val paparazzi = paparazziExtension(DeviceConfig.PIXEL_6)

  test("all labels") {
    paparazzi.snapshot {
      Box(
        modifier = Modifier.requiredSize(
          width = 160.dp,
          height = 400.dp
        )
      ) {
        AllLabelsPreview()
      }
    }
  }
})
