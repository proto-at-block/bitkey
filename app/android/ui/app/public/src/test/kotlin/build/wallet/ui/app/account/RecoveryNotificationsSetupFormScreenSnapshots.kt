package build.wallet.ui.app.account

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.account.create.full.onboard.notifications.RecoveryNotificationsSetupFormBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class RecoveryNotificationsSetupFormScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("recovery notifications setup screen") {
    paparazzi.snapshot {
      FormScreen(
        model = RecoveryNotificationsSetupFormBodyModel(
          onAllowNotifications = {},
          onSkip = {},
          onClose = {}
        )
      )
    }
  }
})
