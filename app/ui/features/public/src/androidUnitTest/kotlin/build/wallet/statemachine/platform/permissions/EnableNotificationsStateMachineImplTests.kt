package build.wallet.statemachine.platform.permissions

import app.cash.turbine.plusAssign
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.events.screen.context.PushNotificationEventTrackerScreenIdContext
import build.wallet.analytics.v1.Action.ACTION_APP_PUSH_NOTIFICATIONS_DISABLED
import build.wallet.analytics.v1.Action.ACTION_APP_PUSH_NOTIFICATIONS_ENABLED
import build.wallet.coroutines.turbine.turbines
import build.wallet.platform.permissions.PermissionCheckerMock
import build.wallet.statemachine.account.notifications.NotificationPermissionRequesterMock
import build.wallet.statemachine.core.Retreat
import build.wallet.statemachine.core.RetreatStyle.Back
import build.wallet.statemachine.core.testWithVirtualTime
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class EnableNotificationsStateMachineImplTests : FunSpec({
  val onBackCalls = turbines.create<Unit>("onBack calls")
  val onCompleteCalls = turbines.create<Unit>("onComplete calls")

  val notificationPermissionRequester = NotificationPermissionRequesterMock(turbines::create)
  val eventTracker = EventTrackerMock(turbines::create)

  val stateMachine =
    EnableNotificationsUiStateMachineImpl(
      notificationPermissionRequester = notificationPermissionRequester,
      eventTracker = eventTracker,
      permissionChecker = PermissionCheckerMock(permissionsOn = false)
    )

  afterTest {
    notificationPermissionRequester.reset()
  }

  val props =
    EnableNotificationsUiProps(
      retreat = Retreat(style = Back, onRetreat = { onBackCalls += Unit }),
      onComplete = { onCompleteCalls += Unit },
      rationale = NotificationRationale.Recovery,
      eventTrackerContext = PushNotificationEventTrackerScreenIdContext.APP_RECOVERY
    )

  test("notification already enabled") {
    // we behave the same here as if it was not enabled
    // we usually will not see this state as there should be checks
    // before hand that should bypass using this state machine if enabled
    stateMachine.testWithVirtualTime(props) {
      awaitItem().shouldBeInstanceOf<EnableNotificationsBodyModel>().run {
        notificationPermissionRequester.successful = true
        onClick()
      }
      notificationPermissionRequester.requestNotificationPermissionCalls.awaitItem()

      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_PUSH_NOTIFICATIONS_ENABLED)
      )

      onCompleteCalls.awaitItem().shouldBe(Unit)
    }
  }

  test("enable notifications screen with notification enabled") {
    stateMachine.testWithVirtualTime(props) {
      awaitItem().shouldBeInstanceOf<EnableNotificationsBodyModel>().run {
        notificationPermissionRequester.successful = true
        onClick()
      }
      notificationPermissionRequester.requestNotificationPermissionCalls.awaitItem()

      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_PUSH_NOTIFICATIONS_ENABLED)
      )

      onCompleteCalls.awaitItem().shouldBe(Unit)
    }
  }

  test("enable notifications screen with notification disabled") {
    stateMachine.testWithVirtualTime(props) {
      awaitItem().shouldBeInstanceOf<EnableNotificationsBodyModel>().run {
        notificationPermissionRequester.successful = false
        onClick()
      }
      notificationPermissionRequester.requestNotificationPermissionCalls.awaitItem()

      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_PUSH_NOTIFICATIONS_DISABLED)
      )

      onCompleteCalls.awaitItem().shouldBe(Unit)
    }
  }

  test("onBack gets called") {
    stateMachine.testWithVirtualTime(props) {
      awaitItem().shouldBeInstanceOf<EnableNotificationsBodyModel>().run {
        onBack.shouldNotBeNull().invoke()
      }

      onBackCalls.awaitItem().shouldBe(Unit)
    }
  }
})
