package build.wallet.ui.app.recovery

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.recovery.verification.ChooseRecoveryNotificationVerificationMethodModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class ChooseRecoveryNotificationVerificationMethodFormScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("choose verification method both sms and email") {
    paparazzi.snapshot {
      FormScreen(
        ChooseRecoveryNotificationVerificationMethodModel(
          onBack = {},
          onEmailClick = {},
          onSmsClick = {}
        )
      )
    }
  }

  test("choose verification method sms only") {
    paparazzi.snapshot {
      FormScreen(
        ChooseRecoveryNotificationVerificationMethodModel(
          onBack = {},
          onEmailClick = null,
          onSmsClick = {}
        )
      )
    }
  }

  test("choose verification method email only") {
    paparazzi.snapshot {
      FormScreen(
        ChooseRecoveryNotificationVerificationMethodModel(
          onBack = {},
          onEmailClick = {},
          onSmsClick = null
        )
      )
    }
  }
})
