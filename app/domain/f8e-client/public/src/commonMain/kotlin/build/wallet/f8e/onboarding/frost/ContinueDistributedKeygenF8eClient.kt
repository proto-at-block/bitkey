package build.wallet.f8e.onboarding.frost

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.SoftwareAccountId
import build.wallet.bitkey.f8e.SoftwareKeyDefinitionId
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.frost.SealedRequest
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

interface ContinueDistributedKeygenF8eClient {
  /**
   * Continues distributed key generation of a new FROST key. This may be invoked multiple times.
   */
  suspend fun continueDistributedKeygen(
    f8eEnvironment: F8eEnvironment,
    accountId: SoftwareAccountId,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    softwareKeyDefinitionId: SoftwareKeyDefinitionId,
    sealedRequest: SealedRequest,
    noiseSessionId: String,
  ): Result<Unit, NetworkingError>
}
