package build.wallet.ui.components.explainer

import app.cash.paparazzi.DeviceConfig
import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class ExplainerSnapshots : FunSpec({
  val paparazzi = paparazziExtension(DeviceConfig.PIXEL_6)

  test("explainer with single statement") {
    paparazzi.snapshot {
      ExplainerWithSingleStatementPreview()
    }
  }

  test("explainer with many statements") {
    paparazzi.snapshot {
      ExplainerWithManyStatementsPreview()
    }
  }

  test("numbered explainer with many statements") {
    paparazzi.snapshot {
      NumberedExplainerWithManyStatementsPreview()
    }
  }
})
