package build.wallet.statemachine.recovery.lostapp

import androidx.compose.runtime.Composable
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.coroutines.turbine.turbines
import build.wallet.recovery.StillRecoveringInitiatedRecoveryMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.WaitingForRecoveryDelayPeriodData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachine
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressProps
import build.wallet.statemachine.recovery.RecoveryInProgressUiProps
import build.wallet.statemachine.recovery.RecoveryInProgressUiStateMachine
import build.wallet.statemachine.recovery.lostapp.initiate.InitiatingLostAppRecoveryUiProps
import build.wallet.statemachine.recovery.lostapp.initiate.InitiatingLostAppRecoveryUiStateMachine
import build.wallet.statemachine.ui.awaitBodyMock
import io.kotest.core.spec.style.FunSpec

class LostAppRecoveryUiStateMachineImplTests : FunSpec({

  val initiatingLostAppRecoveryUiStateMachine =
    object : InitiatingLostAppRecoveryUiStateMachine,
      ScreenStateMachineMock<InitiatingLostAppRecoveryUiProps>(
        id = "initiating lost app recovery"
      ) {}

  val recoveryInProgressUiStateMachine =
    object : RecoveryInProgressUiStateMachine,
      ScreenStateMachineMock<RecoveryInProgressUiProps>(
        id = "recovery in progress"
      ) {}

  val recoveryInProgressDataStateMachine =
    object : RecoveryInProgressDataStateMachine {
      @Composable
      override fun model(props: RecoveryInProgressProps) =
        WaitingForRecoveryDelayPeriodData(
          factorToRecover = App,
          delayPeriodStartTime = StillRecoveringInitiatedRecoveryMock.serverRecovery.delayStartTime,
          delayPeriodEndTime = StillRecoveringInitiatedRecoveryMock.serverRecovery.delayEndTime,
          cancel = {}
        )
    }

  val stateMachine =
    LostAppRecoveryUiStateMachineImpl(
      recoveryInProgressDataStateMachine = recoveryInProgressDataStateMachine,
      initiatingLostAppRecoveryUiStateMachine = initiatingLostAppRecoveryUiStateMachine,
      recoveryInProgressUiStateMachine = recoveryInProgressUiStateMachine
    )

  val onRollbackCalls = turbines.create<Unit>("on rollback calls")
  val goToLiteAccountCreationCalls = turbines.create<Unit>("go to lite account creation calls")

  val props = LostAppRecoveryUiProps(
    cloudBackups = emptyList(),
    activeRecovery = null,
    onRollback = { onRollbackCalls.add(Unit) },
    goToLiteAccountCreation = { goToLiteAccountCreationCalls.add(Unit) }
  )

  test("lost app recovery ui -- initiating (no active recovery)") {
    stateMachine.test(props = props) {
      awaitBodyMock<InitiatingLostAppRecoveryUiProps>(
        id = initiatingLostAppRecoveryUiStateMachine.id
      )
    }
  }

  test("lost app recovery ui -- undergoing (with active recovery)") {
    stateMachine.test(
      props = props.copy(activeRecovery = StillRecoveringInitiatedRecoveryMock)
    ) {
      awaitBodyMock<RecoveryInProgressUiProps>(
        id = recoveryInProgressUiStateMachine.id
      )
    }
  }

  test("lost app recovery ui -- rollback") {
    stateMachine.test(props = props) {
      awaitBodyMock<InitiatingLostAppRecoveryUiProps>(
        id = initiatingLostAppRecoveryUiStateMachine.id
      ) {
        onRollback()
      }
      onRollbackCalls.awaitItem()
    }
  }

  test("lost app recovery ui -- go to lite account creation") {
    stateMachine.test(props = props) {
      awaitBodyMock<InitiatingLostAppRecoveryUiProps>(
        id = initiatingLostAppRecoveryUiStateMachine.id
      ) {
        goToLiteAccountCreation()
      }
      goToLiteAccountCreationCalls.awaitItem()
    }
  }
})
