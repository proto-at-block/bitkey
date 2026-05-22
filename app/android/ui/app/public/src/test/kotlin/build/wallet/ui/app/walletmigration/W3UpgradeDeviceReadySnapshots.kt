package build.wallet.ui.app.walletmigration

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.walletmigration.W3UpgradeDeviceReadyBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class W3UpgradeDeviceReadySnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("W3 upgrade device ready screen") {
    paparazzi.snapshot {
      FormScreen(
        W3UpgradeDeviceReadyBodyModel(
          onBack = {},
          onYes = {},
          onNo = {}
        )
      )
    }
  }

})
