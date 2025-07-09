package build.wallet.statemachine.data.recovery.losthardware

import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.recovery.StillRecoveringInitiatedRecoveryMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.WaitingForRecoveryDelayPeriodData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachine
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressProps
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.AwaitingNewHardwareData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.LostHardwareRecoveryInProgressData
import build.wallet.statemachine.data.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryDataStateMachine
import build.wallet.statemachine.data.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryProps
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeTypeOf

class LostHardwareRecoveryDataStateMachineImplTests : FunSpec({

  val initiatingLostHardwareRecoveryDataStateMachine =
    object : InitiatingLostHardwareRecoveryDataStateMachine,
      StateMachineMock<InitiatingLostHardwareRecoveryProps, InitiatingLostHardwareRecoveryData>(
        initialModel = AwaitingNewHardwareData(
          newAppGlobalAuthKey = AppGlobalAuthPublicKeyMock,
          addHardwareKeys = { _, _ -> }
        )
      ) {}

  val recovery = StillRecoveringInitiatedRecoveryMock.copy(factorToRecover = Hardware)

  val recoveryInProgressDataStateMachine =
    object : RecoveryInProgressDataStateMachine,
      StateMachineMock<RecoveryInProgressProps, RecoveryInProgressData>(
        initialModel =
          WaitingForRecoveryDelayPeriodData(
            factorToRecover = Hardware,
            delayPeriodStartTime = recovery.serverRecovery.delayStartTime,
            delayPeriodEndTime = recovery.serverRecovery.delayEndTime,
            cancel = { },
            retryCloudRecovery = null
          )
      ) {}

  val stateMachine =
    LostHardwareRecoveryDataStateMachineImpl(
      initiatingLostHardwareRecoveryDataStateMachine = initiatingLostHardwareRecoveryDataStateMachine,
      recoveryInProgressDataStateMachine = recoveryInProgressDataStateMachine
    )

  val props =
    LostHardwareRecoveryProps(
      account = FullAccountMock,
      hardwareRecovery = null
    )

  test("lost hardware recovery -- recovery absent") {
    stateMachine.test(props = props) {
      awaitItem().shouldBeTypeOf<AwaitingNewHardwareData>()
    }
  }

  test("lost hardware recovery -- recovery present") {
    stateMachine.test(props = props.copy(hardwareRecovery = recovery)) {
      awaitItem().shouldBeTypeOf<LostHardwareRecoveryInProgressData>().let {
        it.recoveryInProgressData.shouldBeTypeOf<WaitingForRecoveryDelayPeriodData>()
      }
    }
  }
})
