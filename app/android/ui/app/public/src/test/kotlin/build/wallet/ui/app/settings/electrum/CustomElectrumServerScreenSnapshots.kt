package build.wallet.ui.app.settings.electrum

import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class CustomElectrumServerScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("custom electrum server screen - custom server disabled") {
    paparazzi.snapshot {
      CustomElectrumServerScreenDisabledPreview()
    }
  }

  test("custom electrum server screen - custom server enabled, confirmation disabled") {
    paparazzi.snapshot {
      CustomElectrumServerScreenEnabledPreview()
    }
  }

  test("custom electrum server screen - custom server enabled, confirmation enabled") {
    paparazzi.snapshot {
      CustomElectrumServerScreenEnabledWithDisablingDialogPreview()
    }
  }
})
