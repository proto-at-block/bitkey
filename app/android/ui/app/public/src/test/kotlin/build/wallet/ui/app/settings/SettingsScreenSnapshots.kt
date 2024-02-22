package build.wallet.ui.app.settings

import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class SettingsScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("settings screen") {
    paparazzi.snapshot {
      SettingsScreenPreview()
    }
  }
})
