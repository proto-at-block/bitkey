package build.wallet.notifications

import build.wallet.email.Email
import build.wallet.phonenumber.PhoneNumber

/**
 * Describes the notification touchpoint statuses of the currently activated keybox.
 * Currently, a keybox can only have one phone number or email set at a time.
 *
 * @property phoneNumberTouchpointId The server-assigned touchpoint ID for the phone number,
 *   if one is stored. Required when building a DISABLE_RECOVERY_PHONE action proof.
 * @property emailTouchpointId The server-assigned touchpoint ID for the email address,
 *   if one is stored. Required when building a DISABLE_RECOVERY_EMAIL action proof.
 */
data class NotificationTouchpointData(
  val phoneNumber: PhoneNumber?,
  val email: Email?,
  val phoneNumberTouchpointId: String? = null,
  val emailTouchpointId: String? = null,
)
