package build.wallet.f8e.mobilepay

import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.RedactedRequestBody
import build.wallet.ktor.result.RedactedResponseBody
import build.wallet.ktor.result.bodyResult
import build.wallet.ktor.result.setRedactedBody
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class MobilePaySigningF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : MobilePaySigningF8eClient {
  override suspend fun signWithSpecificKeyset(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    keysetId: String,
    psbt: Psbt,
  ): Result<Psbt, NetworkingError> {
    return f8eHttpClient.authenticated(f8eEnvironment, fullAccountId)
      .bodyResult<SignTransactionResponse> {
        post("/api/accounts/${fullAccountId.serverId}/keysets/$keysetId/sign-transaction") {
          setRedactedBody(
            SignTransactionRequest(
              psbt = psbt.base64
            )
          )
        }
      }
      .map { body -> psbt.copy(base64 = body.signedPsbt) }
  }

  @Serializable
  private data class SignTransactionRequest(
    val psbt: String,
  ) : RedactedRequestBody

  @Serializable
  private data class SignTransactionResponse(
    @SerialName("tx")
    val signedPsbt: String,
  ) : RedactedResponseBody
}
