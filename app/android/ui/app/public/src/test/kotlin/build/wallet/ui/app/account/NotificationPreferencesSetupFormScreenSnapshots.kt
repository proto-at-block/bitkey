package build.wallet.ui.app.account

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupFormBodyModel
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupFormItemModel
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupFormItemModel.State.Completed
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupFormItemModel.State.NeedsAction
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupFormItemModel.State.Skipped
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class NotificationPreferencesSetupFormScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("notification preferences setup screen") {
    paparazzi.snapshot {
      FormScreen(
        model =
          NotificationPreferencesSetupFormBodyModel(
            pushItem =
              NotificationPreferencesSetupFormItemModel(
                state = NeedsAction,
                onClick = {}
              ),
            smsItem =
              NotificationPreferencesSetupFormItemModel(
                state = NeedsAction,
                onClick = {}
              ),
            emailItem =
              NotificationPreferencesSetupFormItemModel(
                state = NeedsAction,
                onClick = {}
              )
          ).body as FormBodyModel
      )
    }
  }

  test("notification preferences setup screen all complete") {
    paparazzi.snapshot {
      FormScreen(
        model =
          NotificationPreferencesSetupFormBodyModel(
            pushItem =
              NotificationPreferencesSetupFormItemModel(
                state = Completed,
                onClick = {}
              ),
            smsItem =
              NotificationPreferencesSetupFormItemModel(
                state = Completed,
                onClick = {}
              ),
            emailItem =
              NotificationPreferencesSetupFormItemModel(
                state = Completed,
                onClick = {}
              )
          ).body as FormBodyModel
      )
    }
  }

  test("notification preferences setup screen all skipped") {
    paparazzi.snapshot {
      FormScreen(
        model =
          NotificationPreferencesSetupFormBodyModel(
            pushItem =
              NotificationPreferencesSetupFormItemModel(
                state = Skipped,
                onClick = {}
              ),
            smsItem =
              NotificationPreferencesSetupFormItemModel(
                state = Skipped,
                onClick = {}
              ),
            emailItem =
              NotificationPreferencesSetupFormItemModel(
                state = Skipped,
                onClick = {}
              )
          ).body as FormBodyModel
      )
    }
  }
})
