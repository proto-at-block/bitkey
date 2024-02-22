package build.wallet.ui.app.platform.permissions

import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class AskingToGoToSystemScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("AskingToGoToSystemScreen") {
    paparazzi.snapshot {
      PreviewAskingToGoToSystemScreen()
    }
  }
})
