package build.wallet.ui.app.core.form

import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class UpsellContainerSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("upsell container") {
    paparazzi.snapshot {
      UpsellContainerPreview()
    }
  }
})
