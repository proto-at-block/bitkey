package build.wallet.statemachine.recovery.losthardware

import app.cash.turbine.plusAssign
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.crypto.PublicKey
import build.wallet.recovery.Recovery
import build.wallet.recovery.RecoveryStatusServiceMock
import build.wallet.recovery.StillRecoveringHardwareRecoveryMock
import build.wallet.recovery.StillRecoveringInitiatedRecoveryMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.WaitingForRecoveryDelayPeriodData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachine
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressProps
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
        id = "recovery in progress"
      ) {}

  val recoveryInProgressDataStateMachine =
    object : RecoveryInProgressDataStateMachine,
      StateMachineMock<RecoveryInProgressProps, RecoveryInProgressData>(
        initialModel = WaitingForRecoveryDelayPeriodData(
          factorToRecover = Hardware,
          delayPeriodStartTime = StillRecoveringInitiatedRecoveryMock.serverRecovery.delayStartTime,
          delayPeriodEndTime = StillRecoveringInitiatedRecoveryMock.serverRecovery.delayEndTime,
          cancel = {}
        )
      ) {}

  val recoveryStatusService = RecoveryStatusServiceMock(turbine = turbines::create)

  val stateMachine =
    LostHardwareRecoveryUiStateMachineImpl(
      initiatingLostHardwareRecoveryUiStateMachine = initiatingLostHardwareRecoveryUiStateMachine,
      recoveryInProgressUiStateMachine = recoveryInProgressUiStateMachine,
      recoveryInProgressDataStateMachine = recoveryInProgressDataStateMachine,
      recoveryStatusService = recoveryStatusService
    )

  val onExitCalls = turbines.create<Unit>("on exit calls")

  val props =
    LostHardwareRecoveryProps(
      account = FullAccountMock,
      screenPresentationStyle = Modal,
      instructionsStyle = InstructionsStyle.Independent,
      onFoundHardware = {},
      onExit = { onExitCalls += Unit },
      onComplete = {}
    )

  beforeTest {
    recoveryStatusService.reset()
    recoveryInProgressDataStateMachine.reset()
  }

  test("lost hardware recovery ui -- initiating") {
    stateMachine.test(props = props) {
      awaitBodyMock<InitiatingLostHardwareRecoveryProps>(
        id = initiatingLostHardwareRecoveryUiStateMachine.id
      )
    }
  }

  test("lost hardware recovery ui -- undergoing") {
    recoveryStatusService.recoveryStatus.value = StillRecoveringHardwareRecoveryMock

    stateMachine.test(props = props) {
      awaitBodyMock<RecoveryInProgressUiProps>(
        id = recoveryInProgressUiStateMachine.id
      )
    }
  }

  test("lost hardware recovery ui -- leaving undergoing") {
    recoveryStatusService.recoveryStatus.value = StillRecoveringHardwareRecoveryMock

    stateMachine.test(props = props) {
      awaitBodyMock<RecoveryInProgressUiProps>(
        id = recoveryInProgressUiStateMachine.id
      )

      recoveryStatusService.recoveryStatus.value = Recovery.NoActiveRecovery

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      onExitCalls.awaitItem()
    }
  }

  test("uses originalAppGlobalAuthKey from recovery when available") {
    val originalKey = PublicKey<AppGlobalAuthKey>("original-app-global-auth-key")
    recoveryStatusService.recoveryStatus.value =
      StillRecoveringHardwareRecoveryMock.copy(originalAppGlobalAuthKey = originalKey)

    stateMachine.test(props = props) {
      awaitBodyMock<RecoveryInProgressUiProps>(
        id = recoveryInProgressUiStateMachine.id
      )
    }

    recoveryInProgressDataStateMachine.props.oldAppGlobalAuthKey.shouldBe(originalKey)
  }

  test("falls back to keybox authKey when originalAppGlobalAuthKey is null") {
    recoveryStatusService.recoveryStatus.value =
      StillRecoveringHardwareRecoveryMock.copy(originalAppGlobalAuthKey = null)

    stateMachine.test(props = props) {
      awaitBodyMock<RecoveryInProgressUiProps>(
        id = recoveryInProgressUiStateMachine.id
      )
    }

    recoveryInProgressDataStateMachine.props.oldAppGlobalAuthKey
      .shouldBe(FullAccountMock.keybox.activeAppKeyBundle.authKey)
  }
})
