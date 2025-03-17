package build.wallet.f8e.recovery

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.VerifyTouchpointClientErrorCode
import bitkey.notifications.NotificationTouchpoint
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

interface RecoveryNotificationVerificationF8eClient {
  /**
   * Request to F8e to send a verification code to the given touchpoint.
   * We use this for an additional level of verification during recovery, if needed.
   */
  suspend fun sendVerificationCodeToTouchpoint(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    touchpoint: NotificationTouchpoint,
    hardwareProofOfPossession: HwFactorProofOfPossession?,
  ): Result<Unit, NetworkingError>

  /**
   * Request to F8e to verify the given touchpoint with the given code.
   *
   * Note: only one notification verification for recovery can be in progress at a time,
   * so no touchpoint ID is necessary here because it will be assumed it is the latest
   * one that was sent via [sendVerificationCodeToTouchpoint] that is attempting to be verified.
   */
  suspend fun verifyCode(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    verificationCode: String,
    hardwareProofOfPossession: HwFactorProofOfPossession?,
  ): Result<Unit, F8eError<VerifyTouchpointClientErrorCode>>
}
