package build.wallet.ui.app.walletmigration

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.walletmigration.W3UpgradeCompleteBodyModel
import build.wallet.statemachine.walletmigration.W3UpgradeCompleteSheetBodyModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.app.paparazzi.snapshotSheet
import io.kotest.core.spec.style.FunSpec

class W3UpgradeCompleteSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("W3 upgrade complete screen") {
    paparazzi.snapshot {
      FormScreen(
        W3UpgradeCompleteBodyModel(
          onBack = {},
          onDone = {}
        )
      )
    }
  }

  test("W3 upgrade complete sheet") {
    paparazzi.snapshotSheet(
      model = W3UpgradeCompleteSheetBodyModel(
        onBack = {},
        onDone = {}
      )
    )
  }
})
