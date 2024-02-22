package build.wallet.ui.app.account

import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class ChooseAccountAccessScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("create or recover wallet screen") {
    paparazzi.snapshot {
      ChooseAccountAccessScreenPreview()
    }
  }
})
