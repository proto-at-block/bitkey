package build.wallet.ui.app.walletmigration

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.walletmigration.W3UpgradeBlockerBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class W3UpgradeBlockerSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("W3 upgrade blocker screen") {
    paparazzi.snapshot {
      FormScreen(
        W3UpgradeBlockerBodyModel(
          onGetStarted = {},
          onClose = {}
        )
      )
    }
  }
})
