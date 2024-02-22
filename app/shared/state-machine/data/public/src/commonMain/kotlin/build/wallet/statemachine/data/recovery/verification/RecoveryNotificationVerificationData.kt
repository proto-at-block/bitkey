package build.wallet.statemachine.data.recovery.verification

import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.VerifyTouchpointClientErrorCode
import build.wallet.ktor.result.NetworkingError
import build.wallet.notifications.NotificationTouchpoint

/**
 * Represents the data state of verifying a notification touchpoint during a recovery related
 * action (either cancellation or initiation) when indicated as required by the server (F8e
 * will send a [COMMS_VERIFICATION_REQUIRED] error code when this is necessary).
 */
sealed interface RecoveryNotificationVerificationData {
  /**
   * We're loading the touchpoints for the account being recovered.
   * TODO (W-3806): Optimize this by passing through props
   */
  data object LoadingNotificationTouchpointData : RecoveryNotificationVerificationData

  /**
   * Failure state for error when loading notification touchpoints
   */
  data class LoadingNotificationTouchpointFailureData(
    val rollback: () -> Unit,
    val retry: () -> Unit,
    val error: NetworkingError,
  ) : RecoveryNotificationVerificationData

  /**
   * Customer is choosing the notification touchpoint to use to receive a verification code.
   *
   * @property onSmsClick: Callback when SMS row is clicked. If null, no row should be shown.
   * @property onEmailClick: Callback when email row is clicked. If null, no row should be shown.
   */
  data class ChoosingNotificationTouchpointData(
    val rollback: () -> Unit,
    val onSmsClick: (() -> Unit)?,
    val onEmailClick: (() -> Unit)?,
  ) : RecoveryNotificationVerificationData

  /**
   * We are sending the touchpoint to the server to validate it.
   */
  data object SendingNotificationTouchpointToServerData : RecoveryNotificationVerificationData

  /**
   * Failure state for error when sending touchpoint to the server
   */
  data class SendingNotificationTouchpointToServerFailureData(
    val rollback: () -> Unit,
    val retry: () -> Unit,
    val error: NetworkingError,
  ) : RecoveryNotificationVerificationData

  /**
   * Customer is entering verification code they should have received
   */
  data class EnteringVerificationCodeData(
    val rollback: () -> Unit,
    val touchpoint: NotificationTouchpoint,
    val onResendCode: (
      onSuccess: () -> Unit,
      onError: (isConnectivityError: Boolean) -> Unit,
    ) -> Unit,
    val onCodeEntered: (verificationCode: String) -> Unit,
    val lostFactor: PhysicalFactor,
  ) : RecoveryNotificationVerificationData

  /**
   * We are sending the verification code to the server to validate it.
   * @property verificationCode: The code to send to the server
   */
  data object SendingVerificationCodeToServerData : RecoveryNotificationVerificationData

  /**
   * Failure state for error when sending verification code to the server
   */
  data class SendingVerificationCodeToServerFailureData(
    val rollback: () -> Unit,
    val retry: () -> Unit,
    val error: F8eError<VerifyTouchpointClientErrorCode>,
  ) : RecoveryNotificationVerificationData
}
