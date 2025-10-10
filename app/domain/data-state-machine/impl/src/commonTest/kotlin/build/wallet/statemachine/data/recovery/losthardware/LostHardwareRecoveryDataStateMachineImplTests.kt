package build.wallet.statemachine.data.recovery.losthardware

import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.recovery.StillRecoveringInitiatedRecoveryMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.WaitingForRecoveryDelayPeriodData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachine
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressProps
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.LostHardwareRecoveryInProgressData
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeTypeOf

class LostHardwareRecoveryDataStateMachineImplTests : FunSpec({

  val recovery = StillRecoveringInitiatedRecoveryMock.copy(factorToRecover = Hardware)

  val recoveryInProgressDataStateMachine = object : RecoveryInProgressDataStateMachine,
    StateMachineMock<RecoveryInProgressProps, RecoveryInProgressData>(
      initialModel = WaitingForRecoveryDelayPeriodData(
        factorToRecover = Hardware,
        delayPeriodStartTime = recovery.serverRecovery.delayStartTime,
        delayPeriodEndTime = recovery.serverRecovery.delayEndTime,
        cancel = { },
        retryCloudRecovery = null
      )
    ) {}

  val stateMachine = LostHardwareRecoveryDataStateMachineImpl(
    recoveryInProgressDataStateMachine = recoveryInProgressDataStateMachine
  )

  val props = LostHardwareRecoveryProps(
    account = FullAccountMock,
    hardwareRecovery = null
  )

  test("lost hardware recovery -- recovery absent") {
    stateMachine.test(props = props) {
      awaitItem().shouldBeTypeOf<LostHardwareRecoveryData.LostHardwareRecoveryNotStarted>()
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
