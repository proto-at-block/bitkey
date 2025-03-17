package build.wallet.f8e.onboarding

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.crypto.PublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withAppAuthKey
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.client.plugins.withHardwareFactor
import build.wallet.f8e.logging.withDescription
import build.wallet.ktor.result.EmptyRequestBody
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.catching
import build.wallet.ktor.result.setRedactedBody
import build.wallet.mapUnit
import com.github.michaelbull.result.Result
import io.ktor.client.request.put

@BitkeyInject(AppScope::class)
class SetActiveSpendingKeysetF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : SetActiveSpendingKeysetF8eClient {
  override suspend fun set(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    keysetId: String,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, NetworkingError> {
    return f8eHttpClient
      .authenticated()
      .catching {
        put(urlString = "/api/accounts/${fullAccountId.serverId}/keysets/$keysetId") {
          withDescription("Set active spending keyset")
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
          withAppAuthKey(appAuthKey)
          withHardwareFactor(hwFactorProofOfPossession)
          setRedactedBody(EmptyRequestBody)
        }
      }
      .mapUnit()
  }
}
