package build.wallet.f8e.auth

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.UnauthenticatedF8eHttpClient
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.bodyResult
import build.wallet.ktor.result.setRedactedBody
import com.github.michaelbull.result.Result
import io.ktor.client.request.*

@BitkeyInject(AppScope::class)
class NoiseF8eClientImpl(
  private val f8e: UnauthenticatedF8eHttpClient,
) : NoiseF8eClient {
  override suspend fun initiateNoiseSecureChannel(
    f8eEnvironment: F8eEnvironment,
    body: NoiseInitiateBundleRequest,
  ): Result<NoiseInitiateBundleResponse, NetworkingError> =
    f8e
      .unauthenticated()
      .bodyResult<NoiseInitiateBundleResponse> {
        post("/api/secure-channel/initiate") {
          withEnvironment(f8eEnvironment)
          setRedactedBody(body)
        }
      }
}
