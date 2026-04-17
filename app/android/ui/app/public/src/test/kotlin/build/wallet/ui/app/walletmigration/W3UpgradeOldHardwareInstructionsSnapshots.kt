package build.wallet.ui.app.walletmigration

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.walletmigration.W3UpgradeOldHardwareInstructionsBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class W3UpgradeOldHardwareInstructionsSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("W3 upgrade old hardware instructions screen") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
      FormScreen(
        W3UpgradeOldHardwareInstructionsBodyModel(
          onBack = {},
          onContinue = {}
        )
      )
    }
  }

  test("W3 upgrade old hardware instructions screen with design system v2") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
      FormScreen(
        W3UpgradeOldHardwareInstructionsBodyModel(
          onBack = {},
          onContinue = {}
        )
      )
    }
  }
})
