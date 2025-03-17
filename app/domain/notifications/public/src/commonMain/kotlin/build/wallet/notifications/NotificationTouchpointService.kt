package build.wallet.notifications

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.VerifyTouchpointClientErrorCode
import bitkey.notifications.NotificationTouchpoint
import build.wallet.bitkey.f8e.AccountId
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Manages interactions with a user's notification touchpoints, such as email or phone number.
 */
interface NotificationTouchpointService {
  /**
   * Returns the latest [NotificationTouchpointData] stored in the app's database.
   */
  fun notificationTouchpointData(): Flow<NotificationTouchpointData>

  /**
   * Manually syncs the touchpoints from f8e, storing returned touchpoints in the local database.
   */
  suspend fun syncNotificationTouchpoints(
    accountId: AccountId,
  ): Result<List<NotificationTouchpoint>, Error>

  /**
   * Request to F8e to send a verification code to the given touchpoint.
   */
  suspend fun sendVerificationCodeToTouchpoint(
    fullAccountId: FullAccountId,
    touchpoint: NotificationTouchpoint,
    hwProofOfPossession: HwFactorProofOfPossession?,
  ): Result<Unit, Error>

  /**
   * Request to F8e to verify the given touchpoint with the given code.
   *
   * Note: only one notification verification for recovery can be in progress at a time,
   * so no touchpoint ID is necessary here because it will be assumed it is the latest
   * one that was sent via [sendVerificationCodeToTouchpoint] that is attempting to be verified.
   */
  suspend fun verifyCode(
    fullAccountId: FullAccountId,
    verificationCode: String,
    hwProofOfPossession: HwFactorProofOfPossession?,
  ): Result<Unit, F8eError<VerifyTouchpointClientErrorCode>>
}
