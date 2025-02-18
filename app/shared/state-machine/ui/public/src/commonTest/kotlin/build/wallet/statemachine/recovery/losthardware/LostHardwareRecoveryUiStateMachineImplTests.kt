package build.wallet.statemachine.recovery.losthardware

import app.cash.turbine.plusAssign
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.recovery.StillRecoveringInitiatedRecoveryMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.WaitingForRecoveryDelayPeriodData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.AwaitingNewHardwareData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.LostHardwareRecoveryInProgressData
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

  val stateMachine =
    LostHardwareRecoveryUiStateMachineImpl(
      initiatingLostHardwareRecoveryUiStateMachine = initiatingLostHardwareRecoveryUiStateMachine,
      recoveryInProgressUiStateMachine = recoveryInProgressUiStateMachine
    )

  val onExitCalls = turbines.create<Unit>("on exit calls")

  val initiatingProps =
    LostHardwareRecoveryProps(
      account = FullAccountMock,
      lostHardwareRecoveryData =
        AwaitingNewHardwareData(
          newAppGlobalAuthKey = AppGlobalAuthPublicKeyMock,
          addHardwareKeys = { _, _, _ -> }
        ),
      screenPresentationStyle = Modal,
      instructionsStyle = InstructionsStyle.Independent,
      onFoundHardware = {},
      onExit = {
        onExitCalls += Unit
      },
      onComplete = {}
    )

  val undergoingProps =
    initiatingProps.copy(
      lostHardwareRecoveryData =
        LostHardwareRecoveryInProgressData(
          recoveryInProgressData =
            WaitingForRecoveryDelayPeriodData(
              factorToRecover = Hardware,
              delayPeriodEndTime = StillRecoveringInitiatedRecoveryMock.serverRecovery.delayEndTime,
              delayPeriodStartTime = StillRecoveringInitiatedRecoveryMock.serverRecovery.delayStartTime,
              cancel = { },
              retryCloudRecovery = null
            )
        )
    )

  test("lost hardware recovery ui -- initiating") {
    stateMachine.testWithVirtualTime(
      props = initiatingProps
    ) {
      awaitBodyMock<InitiatingLostHardwareRecoveryProps>(
        id = initiatingLostHardwareRecoveryUiStateMachine.id
      )
    }
  }

  test("lost hardware recovery ui -- undergoing") {
    stateMachine.testWithVirtualTime(
      props = undergoingProps
    ) {
      awaitBodyMock<RecoveryInProgressUiProps>(
        id = recoveryInProgressUiStateMachine.id
      )
    }
  }

  test("lost hardware recovery ui -- leaving undergoing") {
    stateMachine.testWithVirtualTime(
      props = undergoingProps
    ) {
      awaitBodyMock<RecoveryInProgressUiProps>(
        id = recoveryInProgressUiStateMachine.id
      )

      updateProps(initiatingProps)

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      onExitCalls.awaitItem()
    }
  }
})
