package build.wallet.ui.app.walletmigration

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.walletmigration.W3UpgradeNewHardwareAuthRotationInstructionsBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class W3UpgradeNewHardwareAuthRotationInstructionsSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("W3 upgrade new hardware auth rotation instructions screen") {
    paparazzi.snapshot {
      FormScreen(
        W3UpgradeNewHardwareAuthRotationInstructionsBodyModel(
          onBack = {},
          onContinue = {}
        )
      )
    }
  }
})
