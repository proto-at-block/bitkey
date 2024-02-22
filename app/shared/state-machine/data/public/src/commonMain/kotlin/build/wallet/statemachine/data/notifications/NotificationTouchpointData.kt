package build.wallet.statemachine.data.notifications

import build.wallet.email.Email
import build.wallet.phonenumber.PhoneNumber

/**
 * Describes the notification touchpoint statuses of the currently activated keybox.
 * Currently, a keybox can only have one phone number or email set at a time.
 */
data class NotificationTouchpointData(
  val phoneNumber: PhoneNumber?,
  val email: Email?,
)
