package build.wallet.ui.app.send

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.ui.app.core.form.PreviewFeeOptionsFormScreen
import io.kotest.core.spec.style.FunSpec

class FeeOptionsScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("fee options screen") {
    paparazzi.snapshot {
      PreviewFeeOptionsFormScreen()
    }
  }
})
