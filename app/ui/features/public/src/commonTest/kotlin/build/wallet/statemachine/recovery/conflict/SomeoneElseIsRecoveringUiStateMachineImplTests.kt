package build.wallet.statemachine.recovery.conflict

import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.recovery.RecoveryConflictServiceError.CommsVerificationRequired
import build.wallet.recovery.RecoveryConflictServiceError.LocalCancelRecoveryConflictError
import build.wallet.recovery.RecoveryConflictServiceFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.recovery.conflict.model.ShowingSomeoneElseIsRecoveringBodyModel
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiProps
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiStateMachine
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilBodyMock
import com.github.michaelbull.result.Err
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class SomeoneElseIsRecoveringUiStateMachineImplTests : FunSpec({
  val recoveryConflictService = RecoveryConflictServiceFake()

  val recoveryNotificationVerificationUiStateMachine =
    object : RecoveryNotificationVerificationUiStateMachine,
      ScreenStateMachineMock<RecoveryNotificationVerificationUiProps>(
        id = "recovery notification verification"
      ) {}

  val stateMachine =
    SomeoneElseIsRecoveringUiStateMachineImpl(
      recoveryConflictService = recoveryConflictService,
      recoveryNotificationVerificationUiStateMachine = recoveryNotificationVerificationUiStateMachine
    )

  beforeTest {
    recoveryConflictService.reset()
  }

  val appRecoveryConflictProps =
    SomeoneElseIsRecoveringUiProps(
      cancelingRecoveryLostFactor = App,
      fullAccountId = FullAccountIdMock
    )

  val hardwareRecoveryConflictProps =
    SomeoneElseIsRecoveringUiProps(
      cancelingRecoveryLostFactor = Hardware,
      fullAccountId = FullAccountIdMock
    )

  test("app-factor journey: prompt -> cancel -> comms verification -> rollback to prompt") {
    recoveryConflictService.cancelResult = Err(CommsVerificationRequired(Error()))

    stateMachine.test(appRecoveryConflictProps) {
      awaitBody<ShowingSomeoneElseIsRecoveringBodyModel> {
        secondaryButton.shouldNotBeNull().onClick()
      }

      awaitUntilBodyMock<RecoveryNotificationVerificationUiProps> {
        fullAccountId.shouldBe(FullAccountIdMock)
        onRollback()
      }

      awaitUntilBody<ShowingSomeoneElseIsRecoveringBodyModel> {
        secondaryButton.shouldNotBeNull().isLoading.shouldBeFalse()
      }
    }
  }

  test("app-factor journey: prompt -> cancel -> failure sheet -> close returns to prompt") {
    recoveryConflictService.cancelResult = Err(LocalCancelRecoveryConflictError(Error()))

    stateMachine.test(appRecoveryConflictProps) {
      awaitBody<ShowingSomeoneElseIsRecoveringBodyModel> {
        secondaryButton.shouldNotBeNull().onClick()
      }

      awaitBody<ShowingSomeoneElseIsRecoveringBodyModel> {
        isLoading.shouldBeTrue()
      }

      awaitItem().bottomSheetModel.shouldNotBeNull()
        .onClosed()

      awaitUntilBody<ShowingSomeoneElseIsRecoveringBodyModel> {
        secondaryButton.shouldNotBeNull().isLoading.shouldBeFalse()
      }
    }
  }

  test("hardware-factor journey: prompt -> cancel without hardware proof") {
    // This state machine is only reachable from FullAccountUiStateMachineImpl, so the user
    // is always logged in and ProofOfPossessionPlugin attaches X-App-Signature automatically.
    // Hardware proof is therefore never required to cancel a conflicting recovery.
    stateMachine.test(hardwareRecoveryConflictProps) {
      awaitBody<ShowingSomeoneElseIsRecoveringBodyModel> {
        secondaryButton.shouldNotBeNull().onClick()
      }

      awaitUntilBody<ShowingSomeoneElseIsRecoveringBodyModel>(
        matching = { recoveryConflictService.latestCancelAccountId == FullAccountIdMock }
      ) {
        isLoading.shouldBeTrue()
        recoveryConflictService.latestCancelAccountId.shouldBe(FullAccountIdMock)
      }
    }
  }
})
