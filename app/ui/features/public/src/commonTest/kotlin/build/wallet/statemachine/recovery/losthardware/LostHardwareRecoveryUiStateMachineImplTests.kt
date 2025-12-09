package build.wallet.statemachine.recovery.losthardware

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import app.cash.turbine.plusAssign
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.recovery.StillRecoveringInitiatedRecoveryMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.WaitingForRecoveryDelayPeriodData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.LostHardwareRecoveryInProgressData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryDataProps
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryDataStateMachine
import build.wallet.statemachine.recovery.RecoveryInProgressUiProps
import build.wallet.statemachine.recovery.RecoveryInProgressUiStateMachine
import build.wallet.statemachine.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryProps
import build.wallet.statemachine.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryUiStateMachine
import build.wallet.statemachine.recovery.losthardware.initiate.InstructionsStyle
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LostHardwareRecoveryUiStateMachineImplTests : FunSpec({

  val initiatingLostHardwareRecoveryUiStateMachine =
    object : InitiatingLostHardwareRecoveryUiStateMachine,
      ScreenStateMachineMock<InitiatingLostHardwareRecoveryProps>(
        id = "initiating lost hardware recovery"
      ) {}

  val recoveryInProgressUiStateMachine =
    object : RecoveryInProgressUiStateMachine,
      ScreenStateMachineMock<RecoveryInProgressUiProps>(
        id = "initiating lost hardware recovery"
      ) {}

  val lostHardwareRecoveryDataState = mutableStateOf<LostHardwareRecoveryData>(
    LostHardwareRecoveryData.LostHardwareRecoveryNotStarted
  )

  val lostHardwareRecoveryDataStateMachine =
    object : LostHardwareRecoveryDataStateMachine {
      @Composable
      override fun model(props: LostHardwareRecoveryDataProps): LostHardwareRecoveryData {
        return lostHardwareRecoveryDataState.value
      }
    }

  beforeTest {
    lostHardwareRecoveryDataState.value = LostHardwareRecoveryData.LostHardwareRecoveryNotStarted
  }

  val stateMachine =
    LostHardwareRecoveryUiStateMachineImpl(
      initiatingLostHardwareRecoveryUiStateMachine = initiatingLostHardwareRecoveryUiStateMachine,
      recoveryInProgressUiStateMachine = recoveryInProgressUiStateMachine,
      lostHardwareRecoveryDataStateMachine = lostHardwareRecoveryDataStateMachine
    )

  val onExitCalls = turbines.create<Unit>("on exit calls")

  val initiatingProps =
    LostHardwareRecoveryProps(
      account = FullAccountMock,
      screenPresentationStyle = Modal,
      instructionsStyle = InstructionsStyle.Independent,
      onFoundHardware = {},
      onExit = {
        onExitCalls += Unit
      },
      onComplete = {}
    )

  val undergoingRecoveryData =
    LostHardwareRecoveryInProgressData(
      recoveryInProgressData =
        WaitingForRecoveryDelayPeriodData(
          factorToRecover = Hardware,
          delayPeriodEndTime = StillRecoveringInitiatedRecoveryMock.serverRecovery.delayEndTime,
          delayPeriodStartTime = StillRecoveringInitiatedRecoveryMock.serverRecovery.delayStartTime,
          cancel = { }
        )
    )

  test("lost hardware recovery ui -- initiating") {
    stateMachine.test(
      props = initiatingProps
    ) {
      awaitBodyMock<InitiatingLostHardwareRecoveryProps>(
        id = initiatingLostHardwareRecoveryUiStateMachine.id
      )
    }
  }

  test("lost hardware recovery ui -- undergoing") {
    lostHardwareRecoveryDataState.value = undergoingRecoveryData

    stateMachine.test(
      props = initiatingProps
    ) {
      awaitBodyMock<RecoveryInProgressUiProps>(
        id = recoveryInProgressUiStateMachine.id
      )
    }
  }

  test("lost hardware recovery ui -- leaving undergoing") {
    lostHardwareRecoveryDataState.value = undergoingRecoveryData

    stateMachine.test(
      props = initiatingProps
    ) {
      awaitBodyMock<RecoveryInProgressUiProps>(
        id = recoveryInProgressUiStateMachine.id
      )

      lostHardwareRecoveryDataState.value = LostHardwareRecoveryData.LostHardwareRecoveryNotStarted

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      onExitCalls.awaitItem()
    }
  }
})
