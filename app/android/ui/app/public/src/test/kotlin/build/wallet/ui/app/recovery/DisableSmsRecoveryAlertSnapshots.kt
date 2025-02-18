package build.wallet.ui.app.recovery

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.settings.full.notifications.disableSmsRecoveryAlertModel
import build.wallet.ui.components.alertdialog.ButtonAlertDialog
import io.kotest.core.spec.style.FunSpec

class DisableSmsRecoveryAlertSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("disable SMS recovery alert") {
    paparazzi.snapshot {
      ButtonAlertDialog(
        disableSmsRecoveryAlertModel({}, {})
      )
    }
  }
})
