package build.wallet.statemachine.recovery.conflict

import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.coroutines.turbine.turbines
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.conflict.NoLongerRecoveringData
import build.wallet.statemachine.ui.clickPrimaryButton
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf

class NoLongerRecoveringUiStateMachineImplTests : FunSpec({

  val stateMachine = NoLongerRecoveringUiStateMachineImpl()

  val showingRecoveryAttemptCancellationDataOnAcknowledgeCalls =
    turbines.create<Unit>(
      "ShowingRecoveryAttemptCancellationData onAcknowledge calls"
    )
  val showingNoLongerRecoveringDataProps =
    NoLongerRecoveringUiProps(
      data =
        NoLongerRecoveringData.ShowingNoLongerRecoveringData(
          canceledRecoveryLostFactor = App,
          onAcknowledge = { showingRecoveryAttemptCancellationDataOnAcknowledgeCalls.add(Unit) }
        )
    )

  val clearingLocalRecoveryDataProps =
    NoLongerRecoveringUiProps(
      data = NoLongerRecoveringData.ClearingLocalRecoveryData(App)
    )

  val clearingLocalRecoveryFailedDataRetryCalls =
    turbines.create<Unit>(
      "ClearingLocalRecoveryFailedData retry calls"
    )
  val clearingLocalRecoveryFailedDataRollbackCalls =
    turbines.create<Unit>(
      "ClearingLocalRecoveryFailedData rollback calls"
    )
  val clearingLocalRecoveryFailedDataProps =
    NoLongerRecoveringUiProps(
      data =
        NoLongerRecoveringData.ClearingLocalRecoveryFailedData(
          error = Error(),
          cancelingRecoveryLostFactor = App,
          rollback = { clearingLocalRecoveryFailedDataRollbackCalls.add(Unit) },
          retry = { clearingLocalRecoveryFailedDataRetryCalls.add(Unit) }
        )
    )

  test("ShowingRecoveryAttemptCancellationData") {
    stateMachine.test(showingNoLongerRecoveringDataProps) {
      val screen = awaitItem()

      screen.bottomSheetModel.shouldBeNull()

      val body = screen.body.shouldBeInstanceOf<FormBodyModel>()
      val primaryButton = body.primaryButton.shouldNotBeNull()
      primaryButton.isLoading.shouldBeFalse()
      primaryButton.onClick()
      showingRecoveryAttemptCancellationDataOnAcknowledgeCalls.awaitItem()
    }
  }

  test("ClearingLocalRecoveryData") {
    stateMachine.test(clearingLocalRecoveryDataProps) {
      val screen = awaitItem()

      screen.bottomSheetModel.shouldBeNull()

      val body = screen.body.shouldBeInstanceOf<FormBodyModel>()
      val primaryButton = body.primaryButton.shouldNotBeNull()
      primaryButton.isLoading.shouldBeTrue()
    }
  }

  test("ClearingLocalRecoveryFailedData") {
    stateMachine.test(clearingLocalRecoveryFailedDataProps) {
      val screen = awaitItem()
      val bottomSheet = screen.bottomSheetModel.shouldNotBeNull()
      bottomSheet.onClosed()
      clearingLocalRecoveryFailedDataRollbackCalls.awaitItem()
      val bottomSheetBody = bottomSheet.body.shouldBeInstanceOf<FormBodyModel>()
      bottomSheetBody.clickPrimaryButton()
      clearingLocalRecoveryFailedDataRetryCalls.awaitItem()
    }
  }
})
