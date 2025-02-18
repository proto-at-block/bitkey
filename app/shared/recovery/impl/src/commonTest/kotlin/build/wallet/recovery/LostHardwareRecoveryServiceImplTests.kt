package build.wallet.recovery

import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.db.DbQueryError
import build.wallet.f8e.F8eEnvironment.Production
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.SpecificClientErrorMock
import build.wallet.f8e.error.code.CancelDelayNotifyRecoveryErrorCode.NO_RECOVERY_EXISTS
import build.wallet.f8e.recovery.CancelDelayNotifyRecoveryF8eClientMock
import build.wallet.ktor.result.HttpError.ServerError
import build.wallet.ktor.test.HttpResponseMock
import build.wallet.recovery.CancelDelayNotifyRecoveryError.F8eCancelDelayNotifyError
import build.wallet.recovery.CancelDelayNotifyRecoveryError.LocalCancelDelayNotifyError
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import build.wallet.testing.shouldBeOkOfType
import com.github.michaelbull.result.Err
import io.kotest.core.spec.style.FunSpec
import io.ktor.http.HttpStatusCode.Companion.InternalServerError

class LostHardwareRecoveryServiceImplTests : FunSpec({

  val cancelDelayNotifyRecoveryF8eClient = CancelDelayNotifyRecoveryF8eClientMock(turbines::create)
  val recoverySyncer = RecoverySyncerMock(
    StillRecoveringInitiatedRecoveryMock,
    turbines::create
  )
  val service = LostHardwareRecoveryServiceImpl(
    cancelDelayNotifyRecoveryF8eClient = cancelDelayNotifyRecoveryF8eClient,
    recoverySyncer = recoverySyncer,
    recoveryLock = RecoveryLock()
  )

  suspend fun LostHardwareRecoveryServiceImpl.cancel() =
    cancelRecovery(
      f8eEnvironment = Production,
      accountId = FullAccountIdMock
    )

  beforeTest {
    cancelDelayNotifyRecoveryF8eClient.reset()
    recoverySyncer.reset()
  }

  test("success") {
    service.cancel().shouldBeOkOfType<Unit>()

    recoverySyncer.clearCalls.awaitItem()
    cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()
  }

  test("success - ignore general 400") {
    cancelDelayNotifyRecoveryF8eClient.cancelResult =
      Err(SpecificClientErrorMock(NO_RECOVERY_EXISTS))

    service.cancel().shouldBeOk()

    recoverySyncer.clearCalls.awaitItem()
    cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()
  }

  test("failure - backend") {
    cancelDelayNotifyRecoveryF8eClient.cancelResult =
      Err(F8eError.ServerError(ServerError(HttpResponseMock(InternalServerError))))

    service.cancel().shouldBeErrOfType<F8eCancelDelayNotifyError>()

    cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()
  }

  test("failure - dao") {
    recoverySyncer.clearCallResult = Err(DbQueryError(IllegalStateException()))

    service.cancel().shouldBeErrOfType<LocalCancelDelayNotifyError>()

    recoverySyncer.clearCalls.awaitItem()
    cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()
  }
})
