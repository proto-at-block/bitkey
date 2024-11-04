package build.wallet.ui.app.core

import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class LoadingSuccessScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("loading state") {
    paparazzi.snapshot {
      LoadingSuccessPreviewLoading()
    }
  }

  test("success state") {
    paparazzi.snapshot {
      LoadingSuccessPreviewSuccess()
    }
  }
})
