package build.wallet.f8e.onboarding

import bitkey.auth.AuthTokenScope
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.logging.withDescription
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.RedactedRequestBody
import build.wallet.ktor.result.catching
import build.wallet.ktor.result.setRedactedBody
import build.wallet.mapUnit
import build.wallet.platform.config.TouchpointPlatform
import com.github.michaelbull.result.Result
import io.ktor.client.request.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@BitkeyInject(AppScope::class)
class AddDeviceTokenF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : AddDeviceTokenF8eClient {
  override suspend fun add(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    token: String,
    touchpointPlatform: TouchpointPlatform,
    authTokenScope: AuthTokenScope,
  ): Result<Unit, NetworkingError> {
    return f8eHttpClient
      .authenticated()
      .catching {
        post(
          urlString = "/api/accounts/${fullAccountId.serverId}/device-token"
        ) {
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId, authTokenScope)
          withDescription("Add device token to server")
          setRedactedBody(
            Request(
              deviceToken = token,
              platform = touchpointPlatform.platform
            )
          )
        }
      }
      .mapUnit()
  }

  @Serializable
  data class Request(
    @SerialName("device_token") val deviceToken: String,
    @SerialName("platform") val platform: String,
  ) : RedactedRequestBody
}
