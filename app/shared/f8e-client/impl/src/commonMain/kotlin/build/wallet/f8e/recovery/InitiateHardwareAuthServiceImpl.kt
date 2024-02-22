package build.wallet.f8e.recovery

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.recovery.InitiateHardwareAuthService.AuthChallenge
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.bodyResult
import build.wallet.logging.logNetworkFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class InitiateHardwareAuthServiceImpl(
  private val f8eHttpClient: F8eHttpClient,
) : InitiateHardwareAuthService {
  override suspend fun start(
    f8eEnvironment: F8eEnvironment,
    currentHardwareAuthKey: HwAuthPublicKey,
  ): Result<AuthChallenge, NetworkingError> {
    return f8eHttpClient.unauthenticated(f8eEnvironment)
      .bodyResult<ResponseBody> {
        post("/api/hw-auth") {
          setBody(Request(pubKey = currentHardwareAuthKey.pubKey.value))
        }
      }
      .map { body ->
        AuthChallenge(FullAccountId(body.accountId), body.challenge, body.session)
      }
      .logNetworkFailure { "Failed to auth with hardware key on f8e" }
  }

  @Serializable
  private data class ResponseBody(
    @SerialName("account_id")
    val accountId: String,
    @SerialName("challenge")
    val challenge: String,
    @SerialName("session")
    val session: String,
  )

  @Serializable
  private data class Request(
    @SerialName("hw_auth_pubkey")
    val pubKey: String,
  )
}
