package build.wallet.f8e.recovery

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.VerifyTouchpointClientErrorCode
import build.wallet.f8e.error.logF8eFailure
import build.wallet.f8e.error.toF8eError
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.catching
import build.wallet.logging.logNetworkFailure
import build.wallet.notifications.NotificationTouchpoint
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class RecoveryNotificationVerificationServiceImpl(
  private val f8eHttpClient: F8eHttpClient,
) : RecoveryNotificationVerificationService {
  override suspend fun sendVerificationCodeToTouchpoint(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    touchpoint: NotificationTouchpoint,
    hardwareProofOfPossession: HwFactorProofOfPossession?,
  ): Result<Unit, NetworkingError> {
    return f8eHttpClient.authenticated(
      f8eEnvironment,
      fullAccountId,
      hwFactorProofOfPossession = hardwareProofOfPossession
    )
      .catching {
        post("/api/accounts/${fullAccountId.serverId}/delay-notify/send-verification-code") {
          setBody(SendVerificationCodeRequest(touchpoint.touchpointId))
        }
      }.map { Unit }
      .logNetworkFailure { "Failed to send verification code during recovery" }
  }

  override suspend fun verifyCode(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    verificationCode: String,
    hardwareProofOfPossession: HwFactorProofOfPossession?,
  ): Result<Unit, F8eError<VerifyTouchpointClientErrorCode>> {
    return f8eHttpClient.authenticated(
      f8eEnvironment,
      fullAccountId,
      hwFactorProofOfPossession = hardwareProofOfPossession
    )
      .catching {
        post("/api/accounts/${fullAccountId.serverId}/delay-notify/verify-code") {
          setBody(VerifyTouchpointRequest(verificationCode))
        }
      }
      .map { Unit }
      .mapError { it.toF8eError<VerifyTouchpointClientErrorCode>() }
      .logF8eFailure { "Failed to verify notification touchpoint during recovery" }
  }

  @Serializable
  data class SendVerificationCodeRequest(
    @SerialName("touchpoint_id")
    val touchpointId: String,
  )

  @Serializable
  data class VerifyTouchpointRequest(
    @SerialName("verification_code")
    val verificationCode: String,
  )
}
