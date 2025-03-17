package build.wallet.f8e.signing

import build.wallet.bitkey.f8e.SoftwareAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.frost.SealedRequest
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface FrostTransactionSigningF8eClient {
  /**
   * Sends over the PSBT and related signing session information to F8e to provide its partial
   * signatures
   */
  suspend fun getSealedPartialSignatures(
    f8eEnvironment: F8eEnvironment,
    softwareAccountId: SoftwareAccountId,
    noiseSessionId: String,
    sealedRequest: SealedRequest,
  ): Result<SealedTransactionSigningResponse, NetworkingError>
}

@Serializable
data class SealedTransactionSigningResponse(
  @SerialName("sealed_response")
  val sealedResponse: String,
)
