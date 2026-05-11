package build.wallet.ui.app.settings.notifications

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.notifications.NotificationPreferenceFormBodyModel
import build.wallet.statemachine.notifications.NotificationPreferencesFormEditingState
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class NotificationPreferencesFormScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension(maxPercentDifference = 0.2)

  test("notifications preferences editing") {
    paparazzi.snapshot {
      FormScreen(
        model = NotificationPreferenceFormBodyModel(
          transactionPush = false,
          updatesPush = false,
          updatesEmail = false,
          onTransactionPushToggle = {},
          onUpdatesPushToggle = {},
          onUpdatesEmailToggle = {},
          formEditingState = NotificationPreferencesFormEditingState.Editing,
          onBack = {},
          continueOnClick = {},
          onMoneyMovementLearnMore = {}
        )
      )
    }
  }

  test("notifications preferences loading") {
    paparazzi.snapshot {
      FormScreen(
        model = NotificationPreferenceFormBodyModel(
          transactionPush = false,
          updatesPush = false,
          updatesEmail = false,
          onTransactionPushToggle = {},
          onUpdatesPushToggle = {},
          onUpdatesEmailToggle = {},
          formEditingState = NotificationPreferencesFormEditingState.Loading,
          onBack = {},
          continueOnClick = {},
          onMoneyMovementLearnMore = {}
        )
      )
    }
  }

  test("notifications preferences overlay") {
    paparazzi.snapshot {
      FormScreen(
        model = NotificationPreferenceFormBodyModel(
          transactionPush = false,
          updatesPush = false,
          updatesEmail = false,
          onTransactionPushToggle = {},
          onUpdatesPushToggle = {},
          onUpdatesEmailToggle = {},
          formEditingState = NotificationPreferencesFormEditingState.Overlay,
          onBack = {},
          continueOnClick = {},
          onMoneyMovementLearnMore = {}
        )
      )
    }
  }

  test("notifications preferences editing updatesPush") {
    paparazzi.snapshot {
      FormScreen(
        model = NotificationPreferenceFormBodyModel(
          transactionPush = false,
          updatesPush = true,
          updatesEmail = false,
          onTransactionPushToggle = {},
          onUpdatesPushToggle = {},
          onUpdatesEmailToggle = {},
          formEditingState = NotificationPreferencesFormEditingState.Editing,
          onBack = {},
          continueOnClick = {},
          onMoneyMovementLearnMore = {}
        )
      )
    }
  }
})
