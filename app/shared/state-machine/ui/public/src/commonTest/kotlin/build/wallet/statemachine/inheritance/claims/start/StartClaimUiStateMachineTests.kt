package build.wallet.statemachine.inheritance.claims.start

import build.wallet.bitkey.inheritance.BeneficiaryPendingClaimFake
import build.wallet.bitkey.relationships.RelationshipId
import build.wallet.coroutines.turbine.turbines
import build.wallet.inheritance.InheritanceServiceMock
import build.wallet.platform.permissions.PermissionCheckerMock
import build.wallet.statemachine.BodyStateMachineMock
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.platform.permissions.EnableNotificationsUiProps
import build.wallet.statemachine.platform.permissions.EnableNotificationsUiStateMachine
import build.wallet.time.DateTimeFormatterMock
import build.wallet.time.TimeZoneProviderMock
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain

class StartClaimUiStateMachineTests : FunSpec({
  val permissionChecker = PermissionCheckerMock(true)
  val enableNotificationsStateMachine = object : EnableNotificationsUiStateMachine, BodyStateMachineMock<EnableNotificationsUiProps>("enable-notifications") {}
  val inheritanceService = InheritanceServiceMock(
    syncCalls = turbines.create("Sync Calls")
  )
  val stateMachine = StartClaimUiStateMachineImpl(
    inheritanceService = inheritanceService,
    notificationsStateMachine = enableNotificationsStateMachine,
    permissionChecker = PermissionCheckerMock(),
    dateTimeFormatter = DateTimeFormatterMock(),
    timeZoneProvider = TimeZoneProviderMock()
  )
  val onExitCalls = turbines.create<Unit>("Exit Claim State Machine")
  val props = StartClaimUiStateMachineProps(
    relationshipId = RelationshipId("fake-relationship-id"),
    onExit = { onExitCalls.add(Unit) }
  )

  beforeTest {
    inheritanceService.startClaimResult = Ok(BeneficiaryPendingClaimFake)
  }

  test("Successful claim") {
    stateMachine.test(props) {
      awaitScreenWithBody<StartClaimEducationBodyModel> {
        onContinue()
      }
      awaitScreenWithBodyModelMock<EnableNotificationsUiProps> {
        onComplete()
      }
      awaitScreenWithBody<StartClaimConfirmationBodyModel> {
        onContinue()
      }
      awaitScreenWithSheetModelBody<StartClaimConfirmationPromptBodyModel> {
        onConfirm()
      }
      awaitScreenWithBody<LoadingSuccessBodyModel> {}
      awaitScreenWithBody<ClaimStartedBodyModel> {
        onClose()
      }
      onExitCalls.awaitItem()
    }
  }

  test("Failed Claim") {
    inheritanceService.startClaimResult = Err(Error("Failed to start claim"))

    stateMachine.test(props) {
      awaitScreenWithBody<StartClaimEducationBodyModel> {
        onContinue()
      }
      awaitScreenWithBodyModelMock<EnableNotificationsUiProps> {
        onComplete()
      }
      awaitScreenWithBody<StartClaimConfirmationBodyModel> {
        onContinue()
      }
      awaitScreenWithSheetModelBody<StartClaimConfirmationPromptBodyModel> {
        onConfirm()
      }
      awaitScreenWithBody<LoadingSuccessBodyModel> {}
      awaitScreenWithBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().apply {
          text.shouldContain("Try again")
          onClick()
        }
      }
      awaitScreenWithBody<LoadingSuccessBodyModel> {}
      awaitScreenWithBody<FormBodyModel> {
        secondaryButton.shouldNotBeNull().apply {
          text.shouldContain("Cancel")
          onClick()
        }
      }
      onExitCalls.awaitItem()
    }
  }

  test("Exit from initial screen") {
    stateMachine.test(props) {
      awaitScreenWithBody<StartClaimEducationBodyModel> {
        onBack()
      }
      onExitCalls.awaitItem()
    }
  }

  test("Back from confirmation screen") {
    stateMachine.test(props) {
      awaitScreenWithBody<StartClaimEducationBodyModel> {
        onContinue()
      }
      awaitScreenWithBodyModelMock<EnableNotificationsUiProps> {
        onComplete()
      }
      awaitScreenWithBody<StartClaimConfirmationBodyModel> {
        onBack()
      }
      awaitScreenWithBody<StartClaimEducationBodyModel> {}
    }
  }

  test("Close Confirmation Prompt") {
    stateMachine.test(props) {
      awaitScreenWithBody<StartClaimEducationBodyModel> {
        onContinue()
      }
      awaitScreenWithBodyModelMock<EnableNotificationsUiProps> {
        onComplete()
      }
      awaitScreenWithBody<StartClaimConfirmationBodyModel> {
        onContinue()
      }
      awaitScreenWithSheetModelBody<StartClaimConfirmationPromptBodyModel> {
        onBack()
      }
      awaitScreenWithBody<StartClaimConfirmationBodyModel> {}
    }
  }
})
