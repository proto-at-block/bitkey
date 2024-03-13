package build.wallet.ui.app.account

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.account.create.full.onboard.notifications.EmailRecoveryMethodRequiredErrorModal
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class EmailRecoveryMethodRequiredErrorSheetFormScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("Email recovery method required error sheet model screen") {
    paparazzi.snapshot {
      FormScreen(
        EmailRecoveryMethodRequiredErrorModal { }.body as FormBodyModel
      )
    }
  }
})
