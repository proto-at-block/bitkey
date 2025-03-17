package build.wallet.f8e.auth

import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.RedactedRequestBody
import build.wallet.ktor.result.RedactedResponseBody
import com.github.michaelbull.result.Result
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface NoiseF8eClient {
  /**
   * Initiates a Noise secure channel with WSM.
   *
   * @param f8eEnvironment The environment to establish the Noise secure channel with.
   * @param body The request body to send to the server.
   */
  suspend fun initiateNoiseSecureChannel(
    f8eEnvironment: F8eEnvironment,
    body: NoiseInitiateBundleRequest,
  ): Result<NoiseInitiateBundleResponse, NetworkingError>
}

@Serializable
data class NoiseInitiateBundleRequest(
  @SerialName("bundle")
  val bundleBase64: String,
  @SerialName("server_static_pubkey")
  val serverStaticPubkeyBase64: String,
) : RedactedRequestBody

@Serializable
data class NoiseInitiateBundleResponse(
  @SerialName("bundle")
  val bundleBase64: String,
  @SerialName("noise_session")
  val noiseSessionId: String,
) : RedactedResponseBody
