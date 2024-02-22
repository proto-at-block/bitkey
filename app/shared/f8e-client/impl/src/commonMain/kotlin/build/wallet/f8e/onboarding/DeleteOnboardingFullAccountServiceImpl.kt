package build.wallet.f8e.onboarding

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.ktor.result.HttpError
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.catching
import build.wallet.logging.logNetworkFailure
import build.wallet.mapUnit
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.recoverIf
import io.ktor.client.request.delete
import io.ktor.http.HttpStatusCode

class DeleteOnboardingFullAccountServiceImpl(
  private val f8eHttpClient: F8eHttpClient,
) : DeleteOnboardingFullAccountService {
  override suspend fun deleteOnboardingFullAccount(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, NetworkingError> {
    return f8eHttpClient
      .authenticated(
        f8eEnvironment,
        fullAccountId,
        hwFactorProofOfPossession = hwFactorProofOfPossession
      )
      .catching {
        delete("/api/accounts/${fullAccountId.serverId}")
      }
      .recoverIf({ it is HttpError.ClientError && it.response.status == HttpStatusCode.NotFound }) {
      }
      .logNetworkFailure { "Failed to delete account" }
      .mapUnit()
  }
}
