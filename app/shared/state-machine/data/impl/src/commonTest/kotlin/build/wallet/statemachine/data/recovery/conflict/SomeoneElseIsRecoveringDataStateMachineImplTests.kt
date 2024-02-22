package build.wallet.statemachine.data.recovery.conflict

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.recovery.CancelDelayNotifyRecoveryServiceMock
import build.wallet.ktor.result.HttpError
import build.wallet.recovery.Recovery.NoActiveRecovery
import build.wallet.recovery.RecoverySyncerMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringData.AwaitingHardwareProofOfPossessionData
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringData.CancelingSomeoneElsesRecoveryData
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringData.CancelingSomeoneElsesRecoveryFailedData
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringData.ShowingSomeoneElseIsRecoveringData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData.LoadingNotificationTouchpointData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationDataProps
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationDataStateMachine
import com.github.michaelbull.result.Err
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeInstanceOf

class SomeoneElseIsRecoveringDataStateMachineImplTests : FunSpec({

  val cancelDelayNotifyRecoveryService = CancelDelayNotifyRecoveryServiceMock(turbines::create)
  val recoveryNotificationVerificationDataStateMachine =
    object : RecoveryNotificationVerificationDataStateMachine, StateMachineMock<RecoveryNotificationVerificationDataProps, RecoveryNotificationVerificationData>(
      LoadingNotificationTouchpointData
    ) {}
  val recoverySyncer = RecoverySyncerMock(NoActiveRecovery, turbines::create)
  val stateMachine =
    SomeoneElseIsRecoveringDataStateMachineImpl(
      cancelDelayNotifyRecoveryService = cancelDelayNotifyRecoveryService,
      recoveryNotificationVerificationDataStateMachine = recoveryNotificationVerificationDataStateMachine,
      recoverySyncer = recoverySyncer
    )

  val propsOnCloseCalls = turbines.create<Unit>("onClose calls")
  val canceledByLostAppProps =
    SomeoneElseIsRecoveringDataProps(
      cancelingRecoveryLostFactor = App,
      onClose = { propsOnCloseCalls.add(Unit) },
      f8eEnvironment = F8eEnvironment.Development,
      fullAccountId = FullAccountId("account-id")
    )

  val canceledByLostHwProps =
    SomeoneElseIsRecoveringDataProps(
      cancelingRecoveryLostFactor = Hardware,
      onClose = { propsOnCloseCalls.add(Unit) },
      f8eEnvironment = F8eEnvironment.Development,
      fullAccountId = FullAccountId("account-id")
    )

  test("happy path - lost app") {
    stateMachine.test(canceledByLostHwProps) {
      awaitItem().let {
        it.shouldBeInstanceOf<ShowingSomeoneElseIsRecoveringData>()
        it.onCancelRecoveryConflict()
      }
      awaitItem().let {
        it.shouldBeInstanceOf<AwaitingHardwareProofOfPossessionData>()
        it.onComplete(HwFactorProofOfPossession("proof"))
      }

      awaitItem().shouldBeInstanceOf<CancelingSomeoneElsesRecoveryData>()
      cancelDelayNotifyRecoveryService.cancelRecoveryCalls.awaitItem()
      recoverySyncer.clearCalls.awaitItem()
    }
  }

  test("happy path - lost hardware") {
    stateMachine.test(canceledByLostAppProps) {
      awaitItem().let {
        it.shouldBeInstanceOf<ShowingSomeoneElseIsRecoveringData>()
        it.onCancelRecoveryConflict()
      }

      awaitItem().shouldBeInstanceOf<CancelingSomeoneElsesRecoveryData>()
      cancelDelayNotifyRecoveryService.cancelRecoveryCalls.awaitItem()
      recoverySyncer.clearCalls.awaitItem()
    }
  }

  test("cancel recovery failure - retry and rollback") {
    cancelDelayNotifyRecoveryService.cancelResult =
      Err(F8eError.UnhandledException(HttpError.UnhandledException(Throwable())))
    stateMachine.test(canceledByLostHwProps) {
      awaitItem().let {
        it.shouldBeInstanceOf<ShowingSomeoneElseIsRecoveringData>()
        it.onCancelRecoveryConflict()
      }

      awaitItem().let {
        it.shouldBeInstanceOf<AwaitingHardwareProofOfPossessionData>()
        it.onComplete(HwFactorProofOfPossession("proof"))
      }

      awaitItem().shouldBeInstanceOf<CancelingSomeoneElsesRecoveryData>()
      cancelDelayNotifyRecoveryService.cancelRecoveryCalls.awaitItem()

      awaitItem().let {
        it.shouldBeInstanceOf<CancelingSomeoneElsesRecoveryFailedData>()
        it.retry()
      }

      awaitItem().shouldBeInstanceOf<CancelingSomeoneElsesRecoveryData>()
      cancelDelayNotifyRecoveryService.cancelRecoveryCalls.awaitItem()

      awaitItem().let {
        it.shouldBeInstanceOf<CancelingSomeoneElsesRecoveryFailedData>()
        it.rollback()
      }

      awaitItem().shouldBeInstanceOf<ShowingSomeoneElseIsRecoveringData>()
    }
  }
})
