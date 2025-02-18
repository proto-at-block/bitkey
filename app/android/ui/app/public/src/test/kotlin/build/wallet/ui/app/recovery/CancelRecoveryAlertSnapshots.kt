package build.wallet.ui.app.recovery

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.recovery.inprogress.waiting.cancelRecoveryAlertModel
import build.wallet.ui.components.alertdialog.ButtonAlertDialog
import io.kotest.core.spec.style.FunSpec

class CancelRecoveryAlertSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("cancel recovery alert") {
    paparazzi.snapshot {
      ButtonAlertDialog(
        cancelRecoveryAlertModel(onConfirm = {}, onDismiss = {})
      )
    }
  }
})
