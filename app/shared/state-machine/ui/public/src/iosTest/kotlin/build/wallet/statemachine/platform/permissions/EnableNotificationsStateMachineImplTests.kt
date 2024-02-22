package build.wallet.statemachine.platform.permissions

import app.cash.turbine.plusAssign
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.events.screen.context.PushNotificationEventTrackerScreenIdContext
import build.wallet.analytics.v1.Action.ACTION_APP_PUSH_NOTIFICATIONS_ENABLED
import build.wallet.coroutines.turbine.turbines
import build.wallet.platform.permissions.PermissionCheckerMock
import build.wallet.statemachine.account.notifications.NotificationPermissionRequesterMock
import build.wallet.statemachine.core.Retreat
import build.wallet.statemachine.core.RetreatStyle.Back
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class EnableNotificationsStateMachineImplTests : FunSpec({
  val onBackCalls = turbines.create<Unit>("onBack calls")
  val onCompleteCalls = turbines.create<Unit>("onComplete calls")

  val notificationPermissionRequester = NotificationPermissionRequesterMock(turbines::create)
  val eventTracker = EventTrackerMock(turbines::create)

  val stateMachineWithNotificationsOff =
    EnableNotificationsUiStateMachineImpl(
      notificationPermissionRequester = notificationPermissionRequester,
      eventTracker = eventTracker,
      permissionChecker = PermissionCheckerMock(permissionsOn = false)
    )

  val stateMachineWithNotificationsOn =
    EnableNotificationsUiStateMachineImpl(
      notificationPermissionRequester = notificationPermissionRequester,
      eventTracker = eventTracker,
      permissionChecker = PermissionCheckerMock(permissionsOn = true)
    )

  afterTest {
    notificationPermissionRequester.reset()
  }

  val props =
    EnableNotificationsUiProps(
      retreat = Retreat(style = Back, onRetreat = { onBackCalls += Unit }),
      onComplete = { onCompleteCalls += Unit },
      eventTrackerContext = PushNotificationEventTrackerScreenIdContext.APP_RECOVERY
    )

  test("enable notifications screen with notification enabled") {
    stateMachineWithNotificationsOn.test(props) {
      val formModel = awaitItem().shouldBeInstanceOf<FormBodyModel>()
      formModel.primaryButton.shouldNotBeNull().text.shouldBe("Enable")
      notificationPermissionRequester.successful = true
      formModel.primaryButton.shouldNotBeNull().onClick()
      notificationPermissionRequester.requestNotificationPermissionCalls.awaitItem()

      awaitItem()
      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_PUSH_NOTIFICATIONS_ENABLED)
      )
      onCompleteCalls.awaitItem().shouldBe(Unit)
    }
  }

  test("enable notifications screen with notification disabled") {
    stateMachineWithNotificationsOff.test(props) {
      val formModel = awaitItem().shouldBeInstanceOf<FormBodyModel>()
      formModel.primaryButton.shouldNotBeNull().text.shouldBe("Open settings")
    }
  }

  test("onBack gets called") {
    stateMachineWithNotificationsOff.test(props) {
      val formModel = awaitItem().shouldBeInstanceOf<FormBodyModel>()
      formModel.onBack?.invoke()

      onBackCalls.awaitItem().shouldBe(Unit)
    }
  }
})
