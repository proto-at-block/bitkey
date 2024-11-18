package build.wallet.f8e.onboarding.frost

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.SoftwareAccountId
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.frost.SealedRequest
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.RedactedResponseBody
import com.github.michaelbull.result.Result
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface InitiateDistributedKeygenF8eClient {
  /**
   * Initiates distributed key generation of a new FROST key.
   */
  suspend fun initiateDistributedKeygen(
    f8eEnvironment: F8eEnvironment,
    accountId: SoftwareAccountId,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    networkType: BitcoinNetworkType,
    sealedRequest: SealedRequest,
  ): Result<InitiateDistributedKeygenResponse, NetworkingError>
}

@Serializable
data class InitiateDistributedKeygenResponse(
  @SerialName("key_definition_id")
  val keyDefinitionId: String,
  @SerialName("sealed_response")
  val sealedResponse: String,
) : RedactedResponseBody
