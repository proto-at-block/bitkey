package build.wallet.f8e.onboarding

import build.wallet.auth.AuthTokenScope
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
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

class AddDeviceTokenServiceImpl(
  private val f8eHttpClient: F8eHttpClient,
) : AddDeviceTokenService {
  override suspend fun add(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    token: String,
    touchpointPlatform: TouchpointPlatform,
    authTokenScope: AuthTokenScope,
  ): Result<Unit, NetworkingError> {
    return f8eHttpClient
      .authenticated(
        accountId = fullAccountId,
        f8eEnvironment = f8eEnvironment,
        authTokenScope = authTokenScope
      )
      .catching {
        post(
          urlString = "/api/accounts/${fullAccountId.serverId}/device-token"
        ) {
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
