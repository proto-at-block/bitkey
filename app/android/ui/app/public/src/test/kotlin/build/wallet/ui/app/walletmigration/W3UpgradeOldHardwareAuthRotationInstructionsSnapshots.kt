package build.wallet.ui.app.walletmigration

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.walletmigration.W3UpgradeOldHardwareAuthRotationInstructionsBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class W3UpgradeOldHardwareAuthRotationInstructionsSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("W3 upgrade old hardware auth rotation instructions screen") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
      FormScreen(
        W3UpgradeOldHardwareAuthRotationInstructionsBodyModel(
          onBack = {},
          onContinue = {},
          onDeferExit = null
        )
      )
    }
  }
})
