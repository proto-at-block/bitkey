package build.wallet.f8e.onboarding.frost

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.SoftwareAccountId
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.logging.withDescription
import build.wallet.f8e.serialization.toJsonString
import build.wallet.frost.SealedRequest
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.RedactedRequestBody
import build.wallet.ktor.result.bodyResult
import build.wallet.ktor.result.setRedactedBody
import com.github.michaelbull.result.Result
import io.ktor.client.request.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class InitiateDistributedKeygenF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : InitiateDistributedKeygenF8eClient {
  override suspend fun initiateDistributedKeygen(
    f8eEnvironment: F8eEnvironment,
    accountId: SoftwareAccountId,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    networkType: BitcoinNetworkType,
    sealedRequest: SealedRequest,
  ): Result<InitiateDistributedKeygenResponse, NetworkingError> {
    return f8eHttpClient
      .authenticated(
        f8eEnvironment = f8eEnvironment,
        accountId = accountId,
        appFactorProofOfPossessionAuthKey = appAuthKey
      )
      .bodyResult<InitiateDistributedKeygenResponse> {
        post(urlString = "/api/accounts/${accountId.serverId}/distributed-keygen") {
          withDescription("Initiate distributed keygen")
          setRedactedBody(
            RequestBody(
              network = networkType.toJsonString(),
              sealedRequest = sealedRequest.value
            )
          )
        }
      }
  }

  @Serializable
  private data class RequestBody(
    @SerialName("network")
    val network: String,
    @SerialName("sealed_request")
    val sealedRequest: String,
  ) : RedactedRequestBody
}
