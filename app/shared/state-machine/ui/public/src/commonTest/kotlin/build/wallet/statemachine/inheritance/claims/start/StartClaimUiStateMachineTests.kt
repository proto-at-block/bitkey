package build.wallet.statemachine.inheritance.claims.start

import build.wallet.bitkey.inheritance.BeneficiaryPendingClaimFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.relationships.RelationshipId
import build.wallet.coroutines.turbine.turbines
import build.wallet.inheritance.InheritanceServiceMock
import build.wallet.notifications.NotificationChannel
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.full.notifications.NotificationsServiceMock
import build.wallet.statemachine.settings.full.notifications.NotificationsService
import build.wallet.statemachine.settings.full.notifications.RecoveryChannelSettingsProps
import build.wallet.statemachine.settings.full.notifications.RecoveryChannelSettingsUiStateMachine
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitSheet
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.time.DateTimeFormatterMock
import build.wallet.time.TimeZoneProviderMock
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain

class StartClaimUiStateMachineTests : FunSpec({
  val recoveryChannelSettingsStateMachine = object : RecoveryChannelSettingsUiStateMachine,
    ScreenStateMachineMock<RecoveryChannelSettingsProps>("recovery-channel-settings") {}
  val inheritanceService = InheritanceServiceMock(
    syncCalls = turbines.create("Sync Calls")
  )
  val notificationsService = NotificationsServiceMock()
  val stateMachine = StartClaimUiStateMachineImpl(
    inheritanceService = inheritanceService,
    notificationChannelStateMachine = recoveryChannelSettingsStateMachine,
    notificationsService = notificationsService,
    dateTimeFormatter = DateTimeFormatterMock(),
    timeZoneProvider = TimeZoneProviderMock()
  )
  val onExitCalls = turbines.create<Unit>("Exit Claim State Machine")
  val props = StartClaimUiStateMachineProps(
    account = FullAccountMock,
    relationshipId = RelationshipId("fake-relationship-id"),
    onExit = { onExitCalls.add(Unit) }
  )

  beforeTest {
    inheritanceService.startClaimResult = Ok(BeneficiaryPendingClaimFake)
    notificationsService.reset()
  }

  test("Successful claim") {
    stateMachine.testWithVirtualTime(props) {
      awaitBody<StartClaimEducationBodyModel> {
        onContinue()
      }
      awaitBody<StartClaimConfirmationBodyModel> {
        onContinue()
      }
      awaitSheet<StartClaimConfirmationPromptBodyModel> {
        onConfirm()
      }
      awaitBody<LoadingSuccessBodyModel> {}
      awaitBody<ClaimStartedBodyModel> {
        onClose()
      }
      onExitCalls.awaitItem()
    }
  }

  test("Critical Notifications Request Shown") {
    notificationsService.criticalNotificationsStatus.value =
      NotificationsService.NotificationStatus.Missing(
        setOf(NotificationChannel.Push)
      )
    stateMachine.testWithVirtualTime(props) {
      awaitBody<StartClaimEducationBodyModel> {
        onContinue()
      }
      awaitBodyMock<RecoveryChannelSettingsProps> {
        onContinue.shouldNotBeNull().invoke()
      }
      awaitBody<StartClaimConfirmationBodyModel> {
        onContinue()
      }
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("Notification Load Error") {
    notificationsService.criticalNotificationsStatus.value =
      NotificationsService.NotificationStatus.Error(
        RuntimeException()
      )
    stateMachine.testWithVirtualTime(props) {
      awaitBody<StartClaimEducationBodyModel> {
        onContinue()
      }
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().apply {
          text.shouldContain("Skip")
          onClick()
        }
      }
      awaitUntilBody<StartClaimConfirmationBodyModel> {
        onContinue()
      }
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("Failed Claim") {
    inheritanceService.startClaimResult = Err(Error("Failed to start claim"))

    stateMachine.testWithVirtualTime(props) {
      awaitBody<StartClaimEducationBodyModel> {
        onContinue()
      }
      awaitBody<StartClaimConfirmationBodyModel> {
        onContinue()
      }
      awaitSheet<StartClaimConfirmationPromptBodyModel> {
        onConfirm()
      }
      awaitBody<LoadingSuccessBodyModel> {}
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().apply {
          text.shouldContain("Try again")
          onClick()
        }
      }
      awaitBody<LoadingSuccessBodyModel> {}
      awaitBody<FormBodyModel> {
        secondaryButton.shouldNotBeNull().apply {
          text.shouldContain("Cancel")
          onClick()
        }
      }
      onExitCalls.awaitItem()
    }
  }

  test("Exit from initial screen") {
    stateMachine.testWithVirtualTime(props) {
      awaitBody<StartClaimEducationBodyModel> {
        onBack()
      }
      onExitCalls.awaitItem()
    }
  }

  test("Back from confirmation screen") {
    stateMachine.testWithVirtualTime(props) {
      awaitBody<StartClaimEducationBodyModel> {
        onContinue()
      }
      awaitBody<StartClaimConfirmationBodyModel> {
        onBack()
      }
      awaitBody<StartClaimEducationBodyModel> {}
    }
  }

  test("Close Confirmation Prompt") {
    stateMachine.testWithVirtualTime(props) {
      awaitBody<StartClaimEducationBodyModel> {
        onContinue()
      }
      awaitBody<StartClaimConfirmationBodyModel> {
        onContinue()
      }
      awaitSheet<StartClaimConfirmationPromptBodyModel> {
        onBack()
      }
      awaitBody<StartClaimConfirmationBodyModel> {}
    }
  }
})
