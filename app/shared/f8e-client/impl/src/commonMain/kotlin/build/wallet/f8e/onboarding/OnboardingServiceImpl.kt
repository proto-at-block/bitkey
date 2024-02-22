package build.wallet.f8e.onboarding

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.catching
import build.wallet.logging.logNetworkFailure
import build.wallet.mapUnit
import com.github.michaelbull.result.Result
import io.ktor.client.request.post
import io.ktor.client.request.setBody

class OnboardingServiceImpl(
  private val f8eHttpClient: F8eHttpClient,
) : OnboardingService {
  override suspend fun completeOnboarding(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<Unit, NetworkingError> {
    return f8eHttpClient.authenticated(f8eEnvironment, fullAccountId)
      .catching {
        post(urlString = "/api/accounts/${fullAccountId.serverId}/complete-onboarding") {
          setBody("{}")
        }
      }
      .logNetworkFailure { "Failed to complete onboarding" }
      .mapUnit()
  }
}
