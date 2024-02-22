package build.wallet.ui.components.explainer

import app.cash.paparazzi.DeviceConfig
import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class StatementSnapshots : FunSpec({
  val paparazzi = paparazziExtension(DeviceConfig.PIXEL_6)

  test("statement with icon, title and body") {
    paparazzi.snapshot {
      StatementWithIconAndTitleAndBodyPreview()
    }
  }

  test("statement with short title and long body") {
    paparazzi.snapshot {
      StatementWithShortTitleAndLongBodyPreview()
    }
  }

  test("statement with title only") {
    paparazzi.snapshot {
      StatementWithTitleOnlyPreview()
    }
  }

  test("statement with long title only") {
    paparazzi.snapshot {
      StatementWithLongTitleOnlyPreview()
    }
  }

  test("statement with body only") {
    paparazzi.snapshot {
      StatementWithBodyOnlyPreview()
    }
  }

  test("statement with long body only") {
    paparazzi.snapshot {
      StatementWithLongBodyOnlyPreview()
    }
  }

  test("numbered statement with title and body") {
    paparazzi.snapshot {
      NumberedStatementWithTitleAndBodyPreview()
    }
  }
})
