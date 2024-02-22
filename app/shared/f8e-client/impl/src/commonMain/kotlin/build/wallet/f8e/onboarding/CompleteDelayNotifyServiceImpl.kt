package build.wallet.f8e.onboarding

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.recovery.CompleteDelayNotifyService
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.catching
import build.wallet.logging.logNetworkFailure
import build.wallet.mapUnit
import com.github.michaelbull.result.Result
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class CompleteDelayNotifyServiceImpl(
  private val f8eHttpClient: F8eHttpClient,
) : CompleteDelayNotifyService {
  override suspend fun complete(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    challenge: String,
    appSignature: String,
    hardwareSignature: String,
  ): Result<Unit, NetworkingError> {
    return f8eHttpClient.authenticated(f8eEnvironment, fullAccountId)
      .catching {
        post(urlString = "/api/accounts/${fullAccountId.serverId}/delay-notify/complete") {
          setBody(
            Request(
              challenge = challenge,
              appSignature = appSignature,
              hardwareSignature = hardwareSignature
            )
          )
        }
      }
      .logNetworkFailure { "Completing Delay & Notify on f8e failed" }
      .mapUnit()
  }

  @Serializable
  private data class Request(
    @SerialName("challenge")
    val challenge: String,
    @SerialName("app_signature")
    val appSignature: String,
    @SerialName("hardware_signature")
    val hardwareSignature: String,
  )
}
