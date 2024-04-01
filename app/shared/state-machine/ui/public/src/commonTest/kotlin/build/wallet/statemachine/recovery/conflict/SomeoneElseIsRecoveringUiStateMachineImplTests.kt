package build.wallet.statemachine.recovery.conflict

import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.keybox.FullAccountConfigMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringData
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiProps
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiStateMachine
import build.wallet.statemachine.ui.clickPrimaryButton
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf

class SomeoneElseIsRecoveringUiStateMachineImplTests : FunSpec({

  val proofOfPossessionUiStateMachine =
    object : ProofOfPossessionNfcStateMachine,
      ScreenStateMachineMock<ProofOfPossessionNfcProps>(
        id = "proof of possession"
      ) {}

  val recoveryNotificationVerificationUiStateMachine =
    object : RecoveryNotificationVerificationUiStateMachine,
      ScreenStateMachineMock<RecoveryNotificationVerificationUiProps>(
        id = "recovery notification verification"
      ) {}

  val stateMachine =
    SomeoneElseIsRecoveringUiStateMachineImpl(
      proofOfPossessionNfcStateMachine = proofOfPossessionUiStateMachine,
      recoveryNotificationVerificationUiStateMachine = recoveryNotificationVerificationUiStateMachine
    )

  val showingSomeoneElseIsRecoveringDataOnCancelRecoveryConflictCalls =
    turbines.create<Unit>(
      "ShowingSomeoneElseIsRecoveringData onCancelRecoveryConflict calls"
    )
  val showingSomeoneElseIsRecoveringDataProps =
    SomeoneElseIsRecoveringUiProps(
      data =
        SomeoneElseIsRecoveringData.ShowingSomeoneElseIsRecoveringData(
          cancelingRecoveryLostFactor = App,
          onCancelRecoveryConflict = {
            showingSomeoneElseIsRecoveringDataOnCancelRecoveryConflictCalls.add(Unit)
          }
        ),
      fullAccountConfig = FullAccountConfigMock,
      fullAccountId = FullAccountIdMock
    )

  val cancelingSomeoneElsesRecoveryDataProps =
    SomeoneElseIsRecoveringUiProps(
      data =
        SomeoneElseIsRecoveringData.CancelingSomeoneElsesRecoveryData(
          cancelingRecoveryLostFactor = App
        ),
      fullAccountConfig = FullAccountConfigMock,
      fullAccountId = FullAccountIdMock
    )

  val cancelingSomeoneElsesRecoveryFailedDataRetryCalls =
    turbines.create<Unit>(
      "CancelingSomeoneElsesRecoveryFailedData retry calls"
    )
  val cancelingSomeoneElsesRecoveryFailedDataRollbackCalls =
    turbines.create<Unit>(
      "CancelingSomeoneElsesRecoveryFailedData rollback calls"
    )
  val cancelingSomeoneElsesRecoveryFailedDataProps =
    SomeoneElseIsRecoveringUiProps(
      data =
        SomeoneElseIsRecoveringData.CancelingSomeoneElsesRecoveryFailedData(
          error = Error(),
          cancelingRecoveryLostFactor = App,
          rollback = { cancelingSomeoneElsesRecoveryFailedDataRollbackCalls.add(Unit) },
          retry = { cancelingSomeoneElsesRecoveryFailedDataRetryCalls.add(Unit) }
        ),
      fullAccountConfig = FullAccountConfigMock,
      fullAccountId = FullAccountIdMock
    )

  test("ShowingSomeoneElseIsRecoveringData") {
    stateMachine.test(showingSomeoneElseIsRecoveringDataProps) {
      val screen = awaitItem()

      screen.bottomSheetModel.shouldBeNull()

      val body = screen.body.shouldBeInstanceOf<FormBodyModel>()
      val secondaryButton = body.secondaryButton.shouldNotBeNull()
      secondaryButton.isLoading.shouldBeFalse()
      secondaryButton.onClick()
      showingSomeoneElseIsRecoveringDataOnCancelRecoveryConflictCalls.awaitItem()
    }
  }

  test("CancelingSomeoneElsesRecoveryData") {
    stateMachine.test(cancelingSomeoneElsesRecoveryDataProps) {
      val screen = awaitItem()

      screen.bottomSheetModel.shouldBeNull()

      val body = screen.body.shouldBeInstanceOf<FormBodyModel>()
      val secondaryButton = body.secondaryButton.shouldNotBeNull()
      secondaryButton.isLoading.shouldBeTrue()
    }
  }

  test("CancelingSomeoneElsesRecoveryFailedData") {
    stateMachine.test(cancelingSomeoneElsesRecoveryFailedDataProps) {
      val screen = awaitItem()
      val bottomSheet = screen.bottomSheetModel.shouldNotBeNull()
      bottomSheet.onClosed()
      cancelingSomeoneElsesRecoveryFailedDataRollbackCalls.awaitItem()
      val bottomSheetBody = bottomSheet.body.shouldBeInstanceOf<FormBodyModel>()
      bottomSheetBody.clickPrimaryButton()
      cancelingSomeoneElsesRecoveryFailedDataRetryCalls.awaitItem()
    }
  }
})
