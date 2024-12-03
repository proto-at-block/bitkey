package build.wallet.ui.components.label

import app.cash.paparazzi.DeviceConfig
import build.wallet.kotest.paparazzi.paparazziExtension
import com.android.ide.common.rendering.api.SessionParams
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
  val paparazzi = paparazziExtension(
    renderingMode = SessionParams.RenderingMode.FULL_EXPAND
  )

  test("all labels") {
    paparazzi.snapshot {
      AllLabelsPreview()
    }
  }
})
