package build.wallet.f8e.onboarding

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.logging.withDescription
import build.wallet.f8e.recovery.CompleteDelayNotifyF8eClient
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.RedactedRequestBody
import build.wallet.ktor.result.catching
import build.wallet.ktor.result.setRedactedBody
import build.wallet.mapUnit
import com.github.michaelbull.result.Result
import io.ktor.client.request.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@BitkeyInject(AppScope::class)
class CompleteDelayNotifyF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : CompleteDelayNotifyF8eClient {
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
          withDescription("Completing Delay & Notify")
          setRedactedBody(
            Request(
              challenge = challenge,
              appSignature = appSignature,
              hardwareSignature = hardwareSignature
            )
          )
        }
      }
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
  ) : RedactedRequestBody
}
