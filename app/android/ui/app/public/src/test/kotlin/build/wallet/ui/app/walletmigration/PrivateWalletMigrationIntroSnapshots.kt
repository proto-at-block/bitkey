package build.wallet.ui.app.walletmigration

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.walletmigration.PrivateWalletMigrationIntroBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class PrivateWalletMigrationIntroSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("Private wallet migration intro screen") {
    paparazzi.snapshot {
      FormScreen(
        PrivateWalletMigrationIntroBodyModel(
          onBack = {},
          onContinue = {},
          onLearnHow = {}
        )
      )
    }
  }
})
