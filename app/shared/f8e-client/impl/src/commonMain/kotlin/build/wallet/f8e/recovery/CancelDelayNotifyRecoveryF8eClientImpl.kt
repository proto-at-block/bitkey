package build.wallet.f8e.recovery

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.CancelDelayNotifyRecoveryErrorCode
import build.wallet.f8e.error.toF8eError
import build.wallet.f8e.logging.withDescription
import build.wallet.ktor.result.EmptyRequestBody
import build.wallet.ktor.result.catching
import build.wallet.ktor.result.setRedactedBody
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import io.ktor.client.request.delete

@BitkeyInject(AppScope::class)
class CancelDelayNotifyRecoveryF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : CancelDelayNotifyRecoveryF8eClient {
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
          withDescription("Cancel recovery")
          setRedactedBody(EmptyRequestBody)
        }
      }.map { Unit }
      .mapError { it.toF8eError<CancelDelayNotifyRecoveryErrorCode>() }
  }
}
