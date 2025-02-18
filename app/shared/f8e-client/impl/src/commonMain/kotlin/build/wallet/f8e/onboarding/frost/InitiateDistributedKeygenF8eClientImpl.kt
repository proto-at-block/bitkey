package build.wallet.f8e.onboarding.frost

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.SoftwareAccountId
import build.wallet.crypto.PublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withAppAuthKey
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.logging.withDescription
import build.wallet.f8e.serialization.toJsonString
import build.wallet.frost.SealedRequest
import build.wallet.ktor.result.*
import com.github.michaelbull.result.Result
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@BitkeyInject(AppScope::class)
class InitiateDistributedKeygenF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : InitiateDistributedKeygenF8eClient {
  override suspend fun initiateDistributedKeygen(
    f8eEnvironment: F8eEnvironment,
    accountId: SoftwareAccountId,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    networkType: BitcoinNetworkType,
    sealedRequest: SealedRequest,
    noiseSessionId: String,
  ): Result<InitiateDistributedKeygenResponse, NetworkingError> =
    f8eHttpClient
      .authenticated()
      .bodyResult<InitiateDistributedKeygenResponse> {
        post(urlString = "/api/accounts/${accountId.serverId}/distributed-keygen") {
          withDescription("Initiate distributed keygen")
          withEnvironment(f8eEnvironment)
          withAccountId(accountId)
          withAppAuthKey(appAuthKey)
          setRedactedBody(
            RequestBody(
              network = networkType.toJsonString(),
              sealedRequest = sealedRequest.value,
              noiseSessionId = noiseSessionId
            )
          )
        }
      }

  @Serializable
  private data class RequestBody(
    @SerialName("network")
    val network: String,
    @SerialName("sealed_request")
    val sealedRequest: String,
    @SerialName("noise_session")
    val noiseSessionId: String,
  ) : RedactedRequestBody
}
