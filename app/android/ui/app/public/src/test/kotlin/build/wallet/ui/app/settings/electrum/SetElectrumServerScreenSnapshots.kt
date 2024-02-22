package build.wallet.ui.app.settings.electrum

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.ui.app.core.form.SetCustomElectrumFormScreenPreview
import io.kotest.core.spec.style.FunSpec

class SetElectrumServerScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("set custom electrum server form screen") {
    paparazzi.snapshot {
      SetCustomElectrumFormScreenPreview()
    }
  }
})
