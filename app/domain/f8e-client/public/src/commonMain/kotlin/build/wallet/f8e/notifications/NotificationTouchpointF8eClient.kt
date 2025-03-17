package build.wallet.f8e.notifications

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.AddTouchpointClientErrorCode
import bitkey.f8e.error.code.VerifyTouchpointClientErrorCode
import bitkey.notifications.NotificationPreferences
import bitkey.notifications.NotificationTouchpoint
import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

interface NotificationTouchpointF8eClient {
  /**
   * Initiates adding the given touchpoint to the customer's account.
   * The touchpoint will only be fully added after successful verification.
   * @return The touchpoint with the server-generated ID.
   */
  suspend fun addTouchpoint(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    touchpoint: NotificationTouchpoint,
  ): Result<NotificationTouchpoint, F8eError<AddTouchpointClientErrorCode>>

  /**
   * Sends the given verification code to the server in order to verify the touchpoint
   * associated with the given touchpoint ID.
   */
  suspend fun verifyTouchpoint(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    touchpointId: String,
    verificationCode: String,
  ): Result<Unit, F8eError<VerifyTouchpointClientErrorCode>>

  /**
   * Activates the touchpoint with the given touchpoint ID.
   * Requires [HwFactorProofOfPossession] after onboarding.
   */
  suspend fun activateTouchpoint(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    touchpointId: String,
    hwFactorProofOfPossession: HwFactorProofOfPossession?,
  ): Result<Unit, NetworkingError>

  /**
   * Returns the current touchpoints set on the given account.
   */
  suspend fun getTouchpoints(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
  ): Result<List<NotificationTouchpoint>, NetworkingError>

  /**
   * Returns the notification preferences for the given account.
   */
  suspend fun getNotificationsPreferences(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
  ): Result<NotificationPreferences, NetworkingError>

  /**
   * Updates the notification preferences for the given account.
   */
  suspend fun updateNotificationsPreferences(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    preferences: NotificationPreferences,
    hwFactorProofOfPossession: HwFactorProofOfPossession?,
  ): Result<Unit, NetworkingError>
}
