package bitkey.f8e.fingerprintreset

import bitkey.f8e.privilegedactions.ContinuePrivilegedActionRequest
import bitkey.f8e.privilegedactions.PrivilegedActionInstance
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.ktor.result.bodyResult
import com.github.michaelbull.result.Result
import io.ktor.client.request.*

@BitkeyInject(AppScope::class)
class FingerprintResetF8eClientImpl(
  override val f8eHttpClient: F8eHttpClient,
) : FingerprintResetF8eClient {
  override suspend fun createPrivilegedAction(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    request: FingerprintResetRequest,
  ): Result<PrivilegedActionInstance, Throwable> {
    return f8eHttpClient.authenticated()
      .bodyResult<PrivilegedActionInstance> {
        post("/api/accounts/${fullAccountId.serverId}/fingerprint-reset") {
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
          setBody(request)
        }
      }
  }

  override suspend fun continuePrivilegedAction(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    request: ContinuePrivilegedActionRequest,
  ): Result<FingerprintResetResponse, Throwable> {
    return f8eHttpClient.authenticated()
      .bodyResult<FingerprintResetResponse> {
        post("/api/accounts/${fullAccountId.serverId}/fingerprint-reset") {
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
          setBody(request)
        }
      }
  }
}
