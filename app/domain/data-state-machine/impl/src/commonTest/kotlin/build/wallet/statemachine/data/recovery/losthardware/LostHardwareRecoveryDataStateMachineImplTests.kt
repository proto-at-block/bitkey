package build.wallet.statemachine.data.recovery.losthardware

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.crypto.PublicKey
import build.wallet.recovery.RecoveryStatusServiceMock
import build.wallet.recovery.StillRecoveringInitiatedRecoveryMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.WaitingForRecoveryDelayPeriodData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressDataStateMachine
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressProps
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.LostHardwareRecoveryInProgressData
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class LostHardwareRecoveryDataStateMachineImplTests : FunSpec({

  val recovery = StillRecoveringInitiatedRecoveryMock.copy(factorToRecover = Hardware)
  val recoveryStatusService = RecoveryStatusServiceMock(turbine = turbines::create)

  val recoveryInProgressDataStateMachine = object : RecoveryInProgressDataStateMachine,
    StateMachineMock<RecoveryInProgressProps, RecoveryInProgressData>(
      initialModel = WaitingForRecoveryDelayPeriodData(
        factorToRecover = Hardware,
        delayPeriodStartTime = recovery.serverRecovery.delayStartTime,
        delayPeriodEndTime = recovery.serverRecovery.delayEndTime,
        cancel = { }
      )
    ) {}

  val stateMachine = LostHardwareRecoveryDataStateMachineImpl(
    recoveryInProgressDataStateMachine = recoveryInProgressDataStateMachine,
    recoveryStatusService = recoveryStatusService
  )

  val props = LostHardwareRecoveryDataProps(
    account = FullAccountMock
  )

  beforeTest {
    recoveryStatusService.reset()
  }

  test("lost hardware recovery -- recovery absent") {
    stateMachine.test(props = props) {
      awaitItem().shouldBeTypeOf<LostHardwareRecoveryData.LostHardwareRecoveryNotStarted>()
    }
  }

  test("lost hardware recovery -- recovery present") {
    recoveryStatusService.recoveryStatus.value = recovery

    stateMachine.test(props = props) {
      awaitItem().shouldBeTypeOf<LostHardwareRecoveryInProgressData>().let {
        it.recoveryInProgressData.shouldBeTypeOf<WaitingForRecoveryDelayPeriodData>()
      }
    }
  }

  test("uses originalAppGlobalAuthKey from recovery when available") {
    val originalKey = PublicKey<AppGlobalAuthKey>("original-app-global-auth-key")
    val recoveryWithOriginalKey = StillRecoveringInitiatedRecoveryMock.copy(
      factorToRecover = Hardware,
      originalAppGlobalAuthKey = originalKey
    )
    recoveryStatusService.recoveryStatus.value = recoveryWithOriginalKey

    stateMachine.test(props = props) {
      awaitItem().shouldBeTypeOf<LostHardwareRecoveryInProgressData>()
    }

    // Verify the originalAppGlobalAuthKey was passed to the child state machine
    recoveryInProgressDataStateMachine.props.oldAppGlobalAuthKey.shouldBe(originalKey)
  }

  test("falls back to keybox authKey when originalAppGlobalAuthKey is null") {
    val recoveryWithNullKey = StillRecoveringInitiatedRecoveryMock.copy(
      factorToRecover = Hardware,
      originalAppGlobalAuthKey = null
    )
    recoveryStatusService.recoveryStatus.value = recoveryWithNullKey

    stateMachine.test(props = props) {
      awaitItem().shouldBeTypeOf<LostHardwareRecoveryInProgressData>()
    }

    // Verify the keybox auth key was used as fallback
    recoveryInProgressDataStateMachine.props.oldAppGlobalAuthKey
      .shouldBe(FullAccountMock.keybox.activeAppKeyBundle.authKey)
  }
})
