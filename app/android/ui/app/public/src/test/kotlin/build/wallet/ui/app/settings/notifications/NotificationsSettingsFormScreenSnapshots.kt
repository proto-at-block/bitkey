package build.wallet.ui.app.settings.notifications

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.settings.full.notifications.NotificationsSettingsFormBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class NotificationsSettingsFormScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("notifications settings") {
    paparazzi.snapshot {
      FormScreen(
        model =
          NotificationsSettingsFormBodyModel(
            smsText = null,
            emailText = null,
            onBack = {},
            onSmsClick = {},
            onEmailClick = {}
          )
      )
    }
  }

  test("notifications settings with text") {
    paparazzi.snapshot {
      FormScreen(
        model =
          NotificationsSettingsFormBodyModel(
            smsText = "(555) 555-5555",
            emailText = "test@mail.com",
            onBack = {},
            onSmsClick = {},
            onEmailClick = {}
          )
      )
    }
  }
})
