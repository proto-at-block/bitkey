package build.wallet.ui.app.account

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.account.create.full.onboard.notifications.RecoveryChannelsSetupFormBodyModel
import build.wallet.statemachine.account.create.full.onboard.notifications.RecoveryChannelsSetupFormItemModel
import build.wallet.statemachine.account.create.full.onboard.notifications.RecoveryChannelsSetupFormItemModel.State.Completed
import build.wallet.statemachine.account.create.full.onboard.notifications.RecoveryChannelsSetupFormItemModel.State.NotCompleted
import build.wallet.statemachine.account.create.full.onboard.notifications.UiErrorHint
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class RecoveryChannelsSetupFormScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("recovery channels setup screen") {
    paparazzi.snapshot {
      FormScreen(
        model =
          RecoveryChannelsSetupFormBodyModel(
            pushItem =
              RecoveryChannelsSetupFormItemModel(
                state = NotCompleted,
                uiErrorHint = UiErrorHint.None,
                onClick = {}
              ),
            smsItem =
              RecoveryChannelsSetupFormItemModel(
                state = NotCompleted,
                uiErrorHint = UiErrorHint.None,
                onClick = {}
              ),
            emailItem =
              RecoveryChannelsSetupFormItemModel(
                state = NotCompleted,
                uiErrorHint = UiErrorHint.None,
                onClick = {}
              ),
            onBack = {},
            learnOnClick = {},
            continueOnClick = {},
            bottomSheetModel = null,
            alertModel = null
          ).body as FormBodyModel
      )
    }
  }

  test("recovery channels setup screen non-us sim us number") {
    paparazzi.snapshot {
      FormScreen(
        model =
          RecoveryChannelsSetupFormBodyModel(
            pushItem =
              RecoveryChannelsSetupFormItemModel(
                state = NotCompleted,
                uiErrorHint = UiErrorHint.None,
                onClick = {}
              ),
            smsItem =
              RecoveryChannelsSetupFormItemModel(
                state = NotCompleted,
                uiErrorHint = UiErrorHint.NotAvailableInYourCountry,
                onClick = {}
              ),
            emailItem =
              RecoveryChannelsSetupFormItemModel(
                state = NotCompleted,
                uiErrorHint = UiErrorHint.None,
                onClick = {}
              ),
            onBack = {},
            learnOnClick = {},
            continueOnClick = {},
            bottomSheetModel = null,
            alertModel = null
          ).body as FormBodyModel
      )
    }
  }

  test("recovery channels setup screen us user") {
    paparazzi.snapshot {
      FormScreen(
        model =
          RecoveryChannelsSetupFormBodyModel(
            pushItem =
              RecoveryChannelsSetupFormItemModel(
                state = NotCompleted,
                uiErrorHint = UiErrorHint.None,
                onClick = {}
              ),
            smsItem = null,
            emailItem =
              RecoveryChannelsSetupFormItemModel(
                state = NotCompleted,
                uiErrorHint = UiErrorHint.None,
                onClick = {}
              ),
            onBack = {},
            learnOnClick = {},
            continueOnClick = {},
            bottomSheetModel = null,
            alertModel = null
          ).body as FormBodyModel
      )
    }
  }

  test("recovery channels setup screen all complete") {
    paparazzi.snapshot {
      FormScreen(
        model =
          RecoveryChannelsSetupFormBodyModel(
            pushItem =
              RecoveryChannelsSetupFormItemModel(
                state = Completed,
                uiErrorHint = UiErrorHint.None,
                onClick = {}
              ),
            smsItem =
              RecoveryChannelsSetupFormItemModel(
                state = Completed,
                uiErrorHint = UiErrorHint.None,
                onClick = {}
              ),
            emailItem =
              RecoveryChannelsSetupFormItemModel(
                state = Completed,
                uiErrorHint = UiErrorHint.None,
                onClick = {}
              ),
            onBack = {},
            learnOnClick = {},
            continueOnClick = {},
            bottomSheetModel = null,
            alertModel = null
          ).body as FormBodyModel
      )
    }
  }
})
