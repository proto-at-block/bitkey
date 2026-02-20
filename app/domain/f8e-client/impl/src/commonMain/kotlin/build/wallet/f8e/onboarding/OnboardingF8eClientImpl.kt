package build.wallet.f8e.onboarding

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.logging.withDescription
import build.wallet.ktor.result.*
import build.wallet.mapUnit
import com.github.michaelbull.result.Result
import io.ktor.client.request.post

@BitkeyInject(AppScope::class)
class OnboardingF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : OnboardingF8eClient {
  override suspend fun completeOnboarding(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<Unit, NetworkingError> {
    return f8eHttpClient.authenticated()
      .catching {
        post(urlString = "/api/accounts/${fullAccountId.serverId}/complete-onboarding") {
          withDescription("Complete onboarding")
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
          setRedactedBody(EmptyRequestBody)
        }
      }
      .mapUnit()
  }

  override suspend fun completeOnboardingV2(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<CompleteOnboardingResponseV2, NetworkingError> =
    f8eHttpClient
      .authenticated()
      .bodyResult<CompleteOnboardingResponseV2> {
        post(urlString = "/api/v2/accounts/${fullAccountId.serverId}/complete-onboarding") {
          withDescription("Complete onboarding V2")
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
          setRedactedBody(EmptyRequestBody)
        }
      }
}
