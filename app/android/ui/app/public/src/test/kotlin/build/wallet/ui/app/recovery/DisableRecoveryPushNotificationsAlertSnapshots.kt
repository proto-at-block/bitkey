package build.wallet.ui.app.recovery

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.settings.full.notifications.disableRecoveryPushNotificationsAlertModel
import build.wallet.ui.components.alertdialog.ButtonAlertDialog
import io.kotest.core.spec.style.FunSpec

class DisableRecoveryPushNotificationsAlertSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("disable recovery push notifications alert") {
    paparazzi.snapshot {
      ButtonAlertDialog(
        disableRecoveryPushNotificationsAlertModel({}, {})
      )
    }
  }
})
