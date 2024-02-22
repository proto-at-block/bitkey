package build.wallet.f8e.recovery

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.CancelDelayNotifyRecoveryErrorCode
import build.wallet.f8e.error.toF8eError
import build.wallet.ktor.result.catching
import build.wallet.logging.logNetworkFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import io.ktor.client.request.delete
import io.ktor.client.request.setBody
import kotlinx.serialization.Serializable

class CancelDelayNotifyRecoveryServiceImpl(
  private val f8eHttpClient: F8eHttpClient,
) : CancelDelayNotifyRecoveryService {
  override suspend fun cancel(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    hwFactorProofOfPossession: HwFactorProofOfPossession?,
  ): Result<Unit, F8eError<CancelDelayNotifyRecoveryErrorCode>> {
    return f8eHttpClient.authenticated(
      f8eEnvironment,
      fullAccountId,
      hwFactorProofOfPossession = hwFactorProofOfPossession
    )
      .catching {
        delete("/api/accounts/${fullAccountId.serverId}/delay-notify") {
          setBody(
            CancelDelayNotifyRequest
          )
        }
      }.map { Unit }
      .logNetworkFailure { "Failed to cancel recovery" }
      .mapError { it.toF8eError<CancelDelayNotifyRecoveryErrorCode>() }
  }

  @Serializable
  data object CancelDelayNotifyRequest
}
