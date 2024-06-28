package build.wallet.f8e.onboarding.frost

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.SoftwareAccountId
import build.wallet.bitkey.f8e.SoftwareKeysetId
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.logging.withDescription
import build.wallet.ktor.result.*
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class InitiateDistributedKeygenF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : InitiateDistributedKeygenF8eClient {
  override suspend fun initiateDistributedKeygen(
    f8eEnvironment: F8eEnvironment,
    accountId: SoftwareAccountId,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
  ): Result<SoftwareKeysetId, NetworkingError> {
    return f8eHttpClient
      .authenticated(
        f8eEnvironment = f8eEnvironment,
        accountId = accountId,
        appFactorProofOfPossessionAuthKey = appAuthKey
      )
      .bodyResult<ResponseBody> {
        post(urlString = "/api/accounts/${accountId.serverId}/distributed-keygen") {
          withDescription("Initiate distributed keygen")
          setRedactedBody(EmptyRequestBody)
        }
      }.map { SoftwareKeysetId(keysetId = it.keysetId) }
  }

  @Serializable
  private data class ResponseBody(
    @SerialName("keyset_id")
    val keysetId: String,
  ) : RedactedResponseBody
}
