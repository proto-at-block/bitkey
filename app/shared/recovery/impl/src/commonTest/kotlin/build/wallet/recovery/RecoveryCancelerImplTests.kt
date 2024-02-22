package build.wallet.recovery

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.coroutines.turbine.turbines
import build.wallet.db.DbQueryError
import build.wallet.f8e.F8eEnvironment.Production
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.SpecificClientErrorMock
import build.wallet.f8e.error.code.CancelDelayNotifyRecoveryErrorCode
import build.wallet.f8e.recovery.CancelDelayNotifyRecoveryServiceMock
import build.wallet.ktor.result.HttpError.ServerError
import build.wallet.ktor.test.HttpResponseMock
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOkOfType
import com.github.michaelbull.result.Err
import io.kotest.core.spec.style.FunSpec
import io.ktor.http.HttpStatusCode.Companion.InternalServerError

class RecoveryCancelerImplTests : FunSpec({

  val cancelDelayNotifyRecoveryService = CancelDelayNotifyRecoveryServiceMock(turbines::create)
  val recoverySyncer =
    RecoverySyncerMock(
      StillRecoveringInitiatedRecoveryMock,
      turbines::create
    )
  val canceler =
    RecoveryCancelerImpl(
      cancelDelayNotifyRecoveryService = cancelDelayNotifyRecoveryService,
      recoverySyncer = recoverySyncer
    )

  // Helper
  suspend fun RecoveryCanceler.cancel() =
    cancel(
      f8eEnvironment = Production,
      fullAccountId = FullAccountId("foo"),
      hwFactorProofOfPossession = HwFactorProofOfPossession("")
    )

  beforeTest {
    cancelDelayNotifyRecoveryService.reset()
    recoverySyncer.reset()
  }

  test("success") {
    canceler.cancel().shouldBeOkOfType<Unit>()

    recoverySyncer.clearCalls.awaitItem()
    cancelDelayNotifyRecoveryService.cancelRecoveryCalls.awaitItem()
  }

  test("success - ignore general 400") {
    cancelDelayNotifyRecoveryService.cancelResult =
      Err(SpecificClientErrorMock(CancelDelayNotifyRecoveryErrorCode.NO_RECOVERY_EXISTS))

    canceler.cancel().shouldBeOkOfType<Unit>()

    recoverySyncer.clearCalls.awaitItem()
    cancelDelayNotifyRecoveryService.cancelRecoveryCalls.awaitItem()
  }

  test("failure - backend") {
    cancelDelayNotifyRecoveryService.cancelResult =
      Err(
        // Actual code isn't checked, but this simulates what we expect.
        F8eError.ServerError(
          error = ServerError(HttpResponseMock(InternalServerError))
        )
      )

    canceler.cancel()
      .shouldBeErrOfType<RecoveryCanceler.RecoveryCancelerError.F8eCancelDelayNotifyError>()

    cancelDelayNotifyRecoveryService.cancelRecoveryCalls.awaitItem()
  }

  test("failure - dao") {
    recoverySyncer.clearCallResult = Err(DbQueryError(IllegalStateException()))

    canceler.cancel()
      .shouldBeErrOfType<RecoveryCanceler.RecoveryCancelerError.FailedToClearRecoveryStateError>()

    recoverySyncer.clearCalls.awaitItem()
    cancelDelayNotifyRecoveryService.cancelRecoveryCalls.awaitItem()
  }
})
