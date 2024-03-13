package build.wallet.ui.app.settings.notifications

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.notifications.NotificationPreferencesFormBodyModel
import build.wallet.statemachine.notifications.NotificationPreferencesFormEditingState
import build.wallet.statemachine.notifications.TosInfo
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.model.label.CallToActionModel
import io.kotest.core.spec.style.FunSpec

class NotificationPreferencesFormScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("notifications preferences editing") {
    paparazzi.snapshot {
      FormScreen(
        model =
          NotificationPreferencesFormBodyModel(
            transactionPush = false,
            updatesPush = false,
            updatesEmail = false,
            onTransactionPushToggle = {},
            onUpdatesPushToggle = {},
            onUpdatesEmailToggle = {},
            formEditingState = NotificationPreferencesFormEditingState.Editing,
            alertModel = null,
            networkingErrorState = null,
            onBack = {},
            tosInfo = TosInfo(
              termsAgree = false,
              onTermsAgreeToggle = {},
              tosLink = {},
              privacyLink = {}
            ),
            ctaModel = null,
            continueOnClick = {}
          ).body as FormBodyModel
      )
    }
  }

  test("notifications preferences editing tos agreed") {
    paparazzi.snapshot {
      FormScreen(
        model =
          NotificationPreferencesFormBodyModel(
            transactionPush = false,
            updatesPush = false,
            updatesEmail = false,
            onTransactionPushToggle = {},
            onUpdatesPushToggle = {},
            onUpdatesEmailToggle = {},
            formEditingState = NotificationPreferencesFormEditingState.Editing,
            alertModel = null,
            networkingErrorState = null,
            onBack = {},
            tosInfo = TosInfo(
              termsAgree = true,
              onTermsAgreeToggle = {},
              tosLink = {},
              privacyLink = {}
            ),
            ctaModel = null,
            continueOnClick = {}
          ).body as FormBodyModel
      )
    }
  }

  test("notifications preferences editing no tos") {
    paparazzi.snapshot {
      FormScreen(
        model =
          NotificationPreferencesFormBodyModel(
            transactionPush = false,
            updatesPush = false,
            updatesEmail = false,
            onTransactionPushToggle = {},
            onUpdatesPushToggle = {},
            onUpdatesEmailToggle = {},
            formEditingState = NotificationPreferencesFormEditingState.Editing,
            alertModel = null,
            networkingErrorState = null,
            onBack = {},
            tosInfo = null,
            ctaModel = null,
            continueOnClick = {}
          ).body as FormBodyModel
      )
    }
  }

  test("notifications preferences loading") {
    paparazzi.snapshot {
      FormScreen(
        model =
          NotificationPreferencesFormBodyModel(
            transactionPush = false,
            updatesPush = false,
            updatesEmail = false,
            onTransactionPushToggle = {},
            onUpdatesPushToggle = {},
            onUpdatesEmailToggle = {},
            formEditingState = NotificationPreferencesFormEditingState.Editing,
            alertModel = null,
            networkingErrorState = null,
            onBack = {},
            tosInfo = TosInfo(
              termsAgree = false,
              onTermsAgreeToggle = {},
              tosLink = {},
              privacyLink = {}
            ),
            ctaModel = null,
            continueOnClick = {}
          ).body as FormBodyModel
      )
    }
  }

  test("notifications preferences overlay") {
    paparazzi.snapshot {
      FormScreen(
        model =
          NotificationPreferencesFormBodyModel(
            transactionPush = false,
            updatesPush = false,
            updatesEmail = false,
            onTransactionPushToggle = {},
            onUpdatesPushToggle = {},
            onUpdatesEmailToggle = {},
            formEditingState = NotificationPreferencesFormEditingState.Overlay,
            alertModel = null,
            networkingErrorState = null,
            onBack = {},
            tosInfo = TosInfo(
              termsAgree = false,
              onTermsAgreeToggle = {},
              tosLink = {},
              privacyLink = {}
            ),
            ctaModel = null,
            continueOnClick = {}
          ).body as FormBodyModel
      )
    }
  }

  test("notifications preferences editing updatesPush") {
    paparazzi.snapshot {
      FormScreen(
        model =
          NotificationPreferencesFormBodyModel(
            transactionPush = false,
            updatesPush = true,
            updatesEmail = false,
            onTransactionPushToggle = {},
            onUpdatesPushToggle = {},
            onUpdatesEmailToggle = {},
            formEditingState = NotificationPreferencesFormEditingState.Editing,
            alertModel = null,
            networkingErrorState = null,
            onBack = {},
            tosInfo = TosInfo(
              termsAgree = false,
              onTermsAgreeToggle = {},
              tosLink = {},
              privacyLink = {}
            ),
            ctaModel = null,
            continueOnClick = {}
          ).body as FormBodyModel
      )
    }
  }

  test("with terms and privacy policy warning") {
    paparazzi.snapshot {
      FormScreen(
        model =
          NotificationPreferencesFormBodyModel(
            transactionPush = false,
            updatesPush = true,
            updatesEmail = false,
            onTransactionPushToggle = {},
            onUpdatesPushToggle = {},
            onUpdatesEmailToggle = {},
            formEditingState = NotificationPreferencesFormEditingState.Editing,
            alertModel = null,
            networkingErrorState = null,
            onBack = {},
            tosInfo = TosInfo(
              termsAgree = false,
              onTermsAgreeToggle = {},
              tosLink = {},
              privacyLink = {}
            ),
            ctaModel = CallToActionModel(
              text = "Agree to our Terms and Privacy Policy to continue.",
              treatment = CallToActionModel.Treatment.WARNING
            ),
            continueOnClick = {}
          ).body as FormBodyModel
      )
    }
  }
})
