package build.wallet.ui.app.settings

import build.wallet.kotest.paparazzi.paparazziExtension
import io.kotest.core.spec.style.FunSpec

class SettingsScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("settings screen") {
    paparazzi.snapshot {
      SettingsScreen()
    }
  }

  test("settings screen with security hub screen coachmark") {
    paparazzi.snapshot {
      SettingsScreen(securityHubClickHandler = {})
    }
  }

  test("settings screen with design system v2 feature flag on") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
      SettingsScreen()
    }
  }
})
