package build.wallet.f8e.recovery

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.HardwareAuthKeyAvailabilityErrorCode
import bitkey.f8e.error.toF8eError
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.crypto.PublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withAppAuthKey
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.logging.withDescription
import build.wallet.ktor.result.RedactedRequestBody
import build.wallet.ktor.result.RedactedResponseBody
import build.wallet.ktor.result.bodyResult
import build.wallet.ktor.result.setRedactedBody
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import io.ktor.client.request.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@BitkeyInject(AppScope::class)
class HardwareAuthKeyAvailabilityF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : HardwareAuthKeyAvailabilityF8eClient {
  override suspend fun checkAvailability(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    hardwareAuthPublicKey: HwAuthPublicKey,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
  ): Result<HardwareAuthKeyAvailabilityStatus, F8eError<HardwareAuthKeyAvailabilityErrorCode>> {
    return f8eHttpClient
      .authenticated()
      .bodyResult<ResponseBody> {
        post("/api/accounts/${fullAccountId.serverId}/hardware-auth-key/availability") {
          withDescription("Check W3 hardware auth key availability")
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
          withAppAuthKey(appAuthKey)
          setRedactedBody(
            RequestBody(
              hardwareAuthPublicKey = hardwareAuthPublicKey.pubKey.value
            )
          )
        }
      }
      .map { it.status }
      .mapError { it.toF8eError<HardwareAuthKeyAvailabilityErrorCode>() }
  }
}

@Serializable
private data class RequestBody(
  @SerialName("hardware_auth_pubkey")
  val hardwareAuthPublicKey: String,
) : RedactedRequestBody

@Serializable
private data class ResponseBody(
  @SerialName("status")
  val status: HardwareAuthKeyAvailabilityStatus,
) : RedactedResponseBody
