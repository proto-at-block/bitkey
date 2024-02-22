package build.wallet.ui.components.progress

import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class LinearProgressIndicatorSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("0% linear progress indicator") {
    paparazzi.snapshot {
      PreviewLinearProgressIndicatorEmpty()
    }
  }

  test("50% linear progress indicator") {
    paparazzi.snapshot {
      PreviewLinearProgressIndicatorHalf()
    }
  }

  test("100% linear progress indicator") {
    paparazzi.snapshot {
      PreviewLinearProgressIndicatorFull()
    }
  }
})
