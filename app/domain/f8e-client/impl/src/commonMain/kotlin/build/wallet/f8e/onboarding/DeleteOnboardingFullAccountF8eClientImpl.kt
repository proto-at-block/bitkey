package build.wallet.f8e.onboarding

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.client.plugins.withHardwareFactor
import build.wallet.ktor.result.HttpError
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.catching
import build.wallet.logging.logNetworkFailure
import build.wallet.mapUnit
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.recoverIf
import io.ktor.client.request.delete
import io.ktor.http.HttpStatusCode

@BitkeyInject(AppScope::class)
class DeleteOnboardingFullAccountF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : DeleteOnboardingFullAccountF8eClient {
  override suspend fun deleteOnboardingFullAccount(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, NetworkingError> {
    return f8eHttpClient
      .authenticated()
      .catching {
        delete("/api/accounts/${fullAccountId.serverId}") {
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
          withHardwareFactor(hwFactorProofOfPossession)
        }
      }
      .recoverIf({ it is HttpError.ClientError && it.response.status == HttpStatusCode.NotFound }) {
      }
      .logNetworkFailure { "Failed to delete account" }
      .mapUnit()
  }
}
