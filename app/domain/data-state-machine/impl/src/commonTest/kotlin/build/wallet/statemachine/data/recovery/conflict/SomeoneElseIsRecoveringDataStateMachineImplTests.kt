package build.wallet.statemachine.data.recovery.conflict

import bitkey.account.AccountConfigServiceFake
import bitkey.f8e.error.F8eError
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.recovery.CancelDelayNotifyRecoveryF8eClientMock
import build.wallet.ktor.result.HttpError
import build.wallet.recovery.Recovery.NoActiveRecovery
import build.wallet.recovery.RecoveryStatusServiceMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringData.*
import com.github.michaelbull.result.Err
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeInstanceOf

class SomeoneElseIsRecoveringDataStateMachineImplTests : FunSpec({

  val cancelDelayNotifyRecoveryF8eClient = CancelDelayNotifyRecoveryF8eClientMock(turbines::create)
  val recoveryStatusService = RecoveryStatusServiceMock(NoActiveRecovery, turbines::create)
  val accountConfigService = AccountConfigServiceFake()
  val stateMachine =
    SomeoneElseIsRecoveringDataStateMachineImpl(
      cancelDelayNotifyRecoveryF8eClient = cancelDelayNotifyRecoveryF8eClient,
      recoveryStatusService = recoveryStatusService,
      accountConfigService = accountConfigService
    )

  val propsOnCloseCalls = turbines.create<Unit>("onClose calls")
  val canceledByLostAppProps =
    SomeoneElseIsRecoveringDataProps(
      cancelingRecoveryLostFactor = App,
      onClose = { propsOnCloseCalls.add(Unit) },
      fullAccountId = FullAccountId("account-id")
    )

  val canceledByLostHwProps =
    SomeoneElseIsRecoveringDataProps(
      cancelingRecoveryLostFactor = Hardware,
      onClose = { propsOnCloseCalls.add(Unit) },
      fullAccountId = FullAccountId("account-id")
    )

  beforeTest {
    accountConfigService.reset()
  }

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
      cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()
      recoveryStatusService.clearCalls.awaitItem()
    }
  }

  test("happy path - lost hardware") {
    stateMachine.test(canceledByLostAppProps) {
      awaitItem().let {
        it.shouldBeInstanceOf<ShowingSomeoneElseIsRecoveringData>()
        it.onCancelRecoveryConflict()
      }

      awaitItem().shouldBeInstanceOf<CancelingSomeoneElsesRecoveryData>()
      cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()
      recoveryStatusService.clearCalls.awaitItem()
    }
  }

  test("cancel recovery failure - retry and rollback") {
    cancelDelayNotifyRecoveryF8eClient.cancelResult =
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
      cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()

      awaitItem().let {
        it.shouldBeInstanceOf<CancelingSomeoneElsesRecoveryFailedData>()
        it.retry()
      }

      awaitItem().shouldBeInstanceOf<CancelingSomeoneElsesRecoveryData>()
      cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()

      awaitItem().let {
        it.shouldBeInstanceOf<CancelingSomeoneElsesRecoveryFailedData>()
        it.rollback()
      }

      awaitItem().shouldBeInstanceOf<ShowingSomeoneElseIsRecoveringData>()
    }
  }
})
