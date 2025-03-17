package build.wallet.statemachine.account.create.full.onboard.notifications

import bitkey.notifications.NotificationsPreferencesCachedProviderMock
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.platform.permissions.PermissionCheckerMock
import build.wallet.platform.settings.SystemSettingsLauncherMock
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.account.notifications.NotificationPermissionRequesterMock
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.notifications.NotificationPreferencesProps
import build.wallet.statemachine.notifications.NotificationPreferencesUiStateMachineImpl
import build.wallet.statemachine.ui.awaitBody
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.list.ListItemAccessory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class NotificationPreferencesUiStateMachineImplTests : FunSpec({
  val stateMachine = NotificationPreferencesUiStateMachineImpl(
    permissionChecker = PermissionCheckerMock(),
    notificationsPreferencesCachedProvider = NotificationsPreferencesCachedProviderMock(),
    systemSettingsLauncher = SystemSettingsLauncherMock(),
    notificationPermissionRequester = NotificationPermissionRequesterMock(turbines::create),
    inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create),
    eventTracker = EventTrackerMock(turbines::create)
  )

  val onCompleteCalls = turbines.create<Unit>("onComplete")

  val props = NotificationPreferencesProps(
    accountId = FullAccountIdMock,
    source = NotificationPreferencesProps.Source.Onboarding,
    onBack = {},
    onComplete = { onCompleteCalls.add(Unit) }
  )

  test("show tos if terms not accepted") {
    stateMachine.testWithVirtualTime(props) {
      // Try and hit "Continue" right away
      awaitBody<FormBodyModel> {
        ctaWarning.shouldBeNull()
        primaryButton.shouldNotBeNull().onClick.shouldNotBeNull().invoke()
      }

      // Assert that we show some terms
      awaitBody<FormBodyModel> {
        ctaWarning.shouldNotBeNull().text.shouldBe("Agree to our Terms and Privacy Policy to continue.")

        // Simulate tapping the ToS button
        val tosListGroup = mainContentList[4].shouldBeInstanceOf<FormMainContentModel.ListGroup>()
        tosListGroup.listGroupModel.items.first().trailingAccessory.shouldNotBeNull()
          .shouldBeInstanceOf<ListItemAccessory.IconAccessory>().onClick.shouldNotBeNull().invoke()
      }

      // Terms warning should go away
      awaitBody<FormBodyModel> {
        ctaWarning.shouldBeNull()
      }

      // Icon should be filled
      awaitBody<FormBodyModel> {
        val tosListGroup = mainContentList[4].shouldBeInstanceOf<FormMainContentModel.ListGroup>()
        tosListGroup.listGroupModel.items.first().trailingAccessory.shouldNotBeNull()
          .shouldBeInstanceOf<ListItemAccessory.IconAccessory>()
          .model.iconImage.shouldBe(IconImage.LocalImage(Icon.SmallIconCheckFilled))
      }
    }
  }

  test("calls onComplete when done") {
    stateMachine.testWithVirtualTime(props) {
      awaitBody<FormBodyModel> {
        // Simulate tapping the ToS button
        val tosListGroup = mainContentList[4].shouldBeInstanceOf<FormMainContentModel.ListGroup>()
        tosListGroup.listGroupModel.items.first().trailingAccessory.shouldNotBeNull()
          .shouldBeInstanceOf<ListItemAccessory.IconAccessory>().onClick.shouldNotBeNull().invoke()

        ctaWarning.shouldBeNull()
      }

      // Re-render the screen with the TOS selected
      awaitBody<FormBodyModel> {
        // Tap Continue
        primaryButton.shouldNotBeNull().onClick.shouldNotBeNull().invoke()
      }

      // Transition to a loading state, where the primary button shows a loading spinner
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().isLoading.shouldBeTrue()
      }

      // Once more go back to the editing state
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().isLoading.shouldBeFalse()
      }
      // Finally, onComplete is called.
      onCompleteCalls.awaitItem()
    }
  }
})
