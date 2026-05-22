package build.wallet.ui.app.walletmigration

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.walletmigration.W3UpgradeIntroBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class W3UpgradeIntroSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("W3 upgrade intro screen") {
    paparazzi.snapshot {
      FormScreen(
        W3UpgradeIntroBodyModel(
          onBack = {},
          onContinue = {}
        )
      )
    }
  }

})
