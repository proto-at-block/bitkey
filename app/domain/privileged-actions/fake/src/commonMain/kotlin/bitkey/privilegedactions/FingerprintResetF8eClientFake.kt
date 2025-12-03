package bitkey.privilegedactions

import bitkey.f8e.fingerprintreset.FingerprintResetF8eClient
import bitkey.f8e.fingerprintreset.FingerprintResetRequest
import bitkey.f8e.fingerprintreset.FingerprintResetResponse
import bitkey.f8e.privilegedactions.*
import bitkey.f8e.privilegedactions.AuthorizationStrategy
import bitkey.f8e.privilegedactions.AuthorizationStrategyType
import bitkey.f8e.privilegedactions.PrivilegedActionInstance
import bitkey.f8e.privilegedactions.PrivilegedActionType
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.encrypt.WsmVerifierMock
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.F8eHttpClientMock
import build.wallet.ktor.result.EmptyResponseBody
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days

class FingerprintResetF8eClientFake(
  clock: Clock,
) : FingerprintResetF8eClient {
  override val f8eHttpClient: F8eHttpClient by lazy { F8eHttpClientMock(WsmVerifierMock()) }

  val getPrivilegedActionInstancesCalls = mutableListOf<Pair<F8eEnvironment, FullAccountId>>()
  val createPrivilegedActionCalls = mutableListOf<FingerprintResetRequest>()
  val continuePrivilegedActionCalls = mutableListOf<ContinuePrivilegedActionRequest>()
  val cancelFingerprintResetCalls = mutableListOf<CancelPrivilegedActionRequest>()

  var getPrivilegedActionInstancesResult: Result<List<PrivilegedActionInstance>, Throwable> = Ok(emptyList())
  var createPrivilegedActionResult: Result<PrivilegedActionInstance, Throwable> = Ok(
    PrivilegedActionInstance(
      id = "mockId",
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        delayStartTime = clock.now(),
        delayEndTime = clock.now().plus(7.days),
        cancellationToken = "mockCancellationToken",
        completionToken = "mockCompletionToken"
      )
    )
  )
  var continuePrivilegedActionResult: Result<FingerprintResetResponse, Throwable> = Ok(
    FingerprintResetResponse(
      version = 1,
      serializedRequest = "mockSerializedRequest",
      appSignature = "mockAppSignature",
      wsmSignature = "mockWsmSignature"
    )
  )
  var cancelFingerprintResetResult: Result<EmptyResponseBody, Throwable> = Ok(EmptyResponseBody)

  override suspend fun getPrivilegedActionInstances(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<List<PrivilegedActionInstance>, Throwable> {
    getPrivilegedActionInstancesCalls.add(f8eEnvironment to fullAccountId)
    return getPrivilegedActionInstancesResult
  }

  override suspend fun createPrivilegedAction(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    request: FingerprintResetRequest,
  ): Result<PrivilegedActionInstance, Throwable> {
    createPrivilegedActionCalls.add(request)
    return createPrivilegedActionResult
  }

  override suspend fun continuePrivilegedAction(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    request: ContinuePrivilegedActionRequest,
  ): Result<FingerprintResetResponse, Throwable> {
    continuePrivilegedActionCalls.add(request)
    return continuePrivilegedActionResult
  }

  override suspend fun cancelFingerprintReset(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    request: CancelPrivilegedActionRequest,
  ): Result<EmptyResponseBody, Throwable> {
    cancelFingerprintResetCalls.add(request)
    return cancelFingerprintResetResult
  }

  fun reset() {
    getPrivilegedActionInstancesCalls.clear()
    createPrivilegedActionCalls.clear()
    continuePrivilegedActionCalls.clear()
    cancelFingerprintResetCalls.clear()
  }
}
