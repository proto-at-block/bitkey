package bitkey.privilegedactions

import app.cash.turbine.Turbine
import bitkey.f8e.fingerprintreset.FingerprintResetF8eClient
import bitkey.f8e.fingerprintreset.FingerprintResetRequest
import bitkey.f8e.fingerprintreset.FingerprintResetResponse
import bitkey.f8e.privilegedactions.AuthorizationStrategy
import bitkey.f8e.privilegedactions.AuthorizationStrategyType
import bitkey.f8e.privilegedactions.CancelPrivilegedActionRequest
import bitkey.f8e.privilegedactions.ContinuePrivilegedActionRequest
import bitkey.f8e.privilegedactions.PrivilegedActionInstance
import bitkey.f8e.privilegedactions.PrivilegedActionType
import bitkey.f8e.privilegedactions.PrivilegedActionsF8eClientFake
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.EmptyResponseBody
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days

class FingerprintResetF8eClientFake(turbine: (String) -> Turbine<Any>) : FingerprintResetF8eClient, PrivilegedActionsF8eClientFake<FingerprintResetRequest, FingerprintResetResponse>(
  turbine
) {
  val cancelFingerprintResetCalls = turbine("cancelFingerprintReset calls") as Turbine<CancelPrivilegedActionRequest>
  var cancelFingerprintResetResult: Result<EmptyResponseBody, Throwable> = Ok(EmptyResponseBody)

  init {
    createPrivilegedActionResult = Ok(
      PrivilegedActionInstance(
        id = "mockId",
        privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
        authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
          authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
          delayEndTime = Clock.System.now().plus(7.days),
          cancellationToken = "mockCancellationToken",
          completionToken = "mockCompletionToken"
        )
      )
    )
  }

  override suspend fun continuePrivilegedAction(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    request: ContinuePrivilegedActionRequest,
  ): Result<FingerprintResetResponse, Throwable> {
    return super.continuePrivilegedAction(f8eEnvironment, fullAccountId, request)
  }

  override suspend fun cancelFingerprintReset(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    request: CancelPrivilegedActionRequest,
  ): Result<EmptyResponseBody, Throwable> {
    cancelFingerprintResetCalls.add(request)
    return cancelFingerprintResetResult
  }
}
