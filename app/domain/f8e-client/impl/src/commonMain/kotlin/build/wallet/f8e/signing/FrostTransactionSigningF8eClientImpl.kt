package build.wallet.f8e.signing

import build.wallet.bitkey.f8e.SoftwareAccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.frost.SealedRequest
import build.wallet.ktor.result.*
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@BitkeyInject(AppScope::class)
class FrostTransactionSigningF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : FrostTransactionSigningF8eClient {
  override suspend fun getSealedPartialSignatures(
    f8eEnvironment: F8eEnvironment,
    softwareAccountId: SoftwareAccountId,
    noiseSessionId: String,
    sealedRequest: SealedRequest,
  ): Result<SealedTransactionSigningResponse, NetworkingError> =
    f8eHttpClient
      .authenticated()
      .bodyResult<FrostTransactionSigningResponse> {
        post("/api/accounts/${softwareAccountId.serverId}/generate-partial-signatures") {
          withEnvironment(f8eEnvironment)
          withAccountId(softwareAccountId)
          setRedactedBody(
            FrostTransactionSigningRequest(
              noiseSessionId = noiseSessionId,
              sealedRequest = sealedRequest.value
            )
          )
        }
      }.map { body -> SealedTransactionSigningResponse(sealedResponse = body.sealedResponse) }

  @Serializable
  private data class FrostTransactionSigningRequest(
    @SerialName("noise_session")
    val noiseSessionId: String,
    @SerialName("sealed_request")
    val sealedRequest: String,
  ) : RedactedRequestBody

  @Serializable
  private data class FrostTransactionSigningResponse(
    @SerialName("sealed_response")
    val sealedResponse: String,
  ) : RedactedResponseBody
}
