package build.wallet.f8e.recovery

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.VerifyTouchpointClientErrorCode
import bitkey.f8e.error.toF8eError
import bitkey.notifications.NotificationTouchpoint
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.client.plugins.withHardwareFactor
import build.wallet.f8e.logging.withDescription
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.RedactedRequestBody
import build.wallet.ktor.result.catching
import build.wallet.ktor.result.setRedactedBody
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import io.ktor.client.request.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@BitkeyInject(AppScope::class)
class RecoveryNotificationVerificationF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : RecoveryNotificationVerificationF8eClient {
  override suspend fun sendVerificationCodeToTouchpoint(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    touchpoint: NotificationTouchpoint,
    hardwareProofOfPossession: HwFactorProofOfPossession?,
  ): Result<Unit, NetworkingError> {
    return f8eHttpClient.authenticated()
      .catching {
        post("/api/accounts/${fullAccountId.serverId}/delay-notify/send-verification-code") {
          withDescription("Send verification code during recovery")
          setRedactedBody(SendVerificationCodeRequest(touchpoint.touchpointId))
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
          hardwareProofOfPossession?.run(::withHardwareFactor)
        }
      }.map { Unit }
  }

  override suspend fun verifyCode(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    verificationCode: String,
    hardwareProofOfPossession: HwFactorProofOfPossession?,
  ): Result<Unit, F8eError<VerifyTouchpointClientErrorCode>> {
    return f8eHttpClient.authenticated()
      .catching {
        post("/api/accounts/${fullAccountId.serverId}/delay-notify/verify-code") {
          withDescription("Verify notification touchpoint during recovery")
          setRedactedBody(VerifyTouchpointRequest(verificationCode))
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
          hardwareProofOfPossession?.run(::withHardwareFactor)
        }
      }
      .map { Unit }
      .mapError { it.toF8eError<VerifyTouchpointClientErrorCode>() }
  }

  @Serializable
  data class SendVerificationCodeRequest(
    @SerialName("touchpoint_id")
    val touchpointId: String,
  ) : RedactedRequestBody

  @Serializable
  data class VerifyTouchpointRequest(
    @SerialName("verification_code")
    val verificationCode: String,
  ) : RedactedRequestBody
}
