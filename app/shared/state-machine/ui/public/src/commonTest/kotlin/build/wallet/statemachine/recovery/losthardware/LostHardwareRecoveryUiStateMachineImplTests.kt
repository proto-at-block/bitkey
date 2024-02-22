package build.wallet.statemachine.recovery.losthardware

import app.cash.turbine.plusAssign
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.keybox.KeyboxConfigMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.money.currency.USD
import build.wallet.recovery.StillRecoveringInitiatedRecoveryMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.WaitingForRecoveryDelayPeriodData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.AwaitingNewHardwareData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.LostHardwareRecoveryInProgressData
import build.wallet.statemachine.recovery.RecoveryInProgressUiProps
import build.wallet.statemachine.recovery.RecoveryInProgressUiStateMachine
import build.wallet.statemachine.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryProps
import build.wallet.statemachine.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryUiStateMachine
import build.wallet.statemachine.recovery.losthardware.initiate.InstructionsStyle
import io.kotest.core.spec.style.FunSpec

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
      keyboxConfig = KeyboxConfigMock,
      fullAccountId = FullAccountIdMock,
      lostHardwareRecoveryData =
        AwaitingNewHardwareData(
          addHardwareKeys = { _, _ -> }
        ),
      screenPresentationStyle = Modal,
      fiatCurrency = USD,
      instructionsStyle = InstructionsStyle.Independent,
      onFoundHardware = {},
      onExit = {
        onExitCalls += Unit
      }
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
    stateMachine.test(
      props = initiatingProps
    ) {
      awaitScreenWithBodyModelMock<InitiatingLostHardwareRecoveryProps>(
        id = initiatingLostHardwareRecoveryUiStateMachine.id
      )
    }
  }

  test("lost hardware recovery ui -- undergoing") {
    stateMachine.test(
      props = undergoingProps
    ) {
      awaitScreenWithBodyModelMock<RecoveryInProgressUiProps>(
        id = recoveryInProgressUiStateMachine.id
      )
    }
  }

  test("lost hardware recovery ui -- leaving undergoing") {
    stateMachine.test(
      props = undergoingProps
    ) {
      awaitScreenWithBodyModelMock<RecoveryInProgressUiProps>(
        id = recoveryInProgressUiStateMachine.id
      )

      updateProps(initiatingProps)

      awaitScreenWithBody<LoadingBodyModel>()
      onExitCalls.awaitItem()
    }
  }
})
