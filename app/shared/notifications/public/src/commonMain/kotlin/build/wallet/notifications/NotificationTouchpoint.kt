package build.wallet.notifications

import build.wallet.email.Email
import build.wallet.phonenumber.PhoneNumber

/**
 * Represents different types of notification touchpoints for a customer's account that
 * can be added to the account using [NotificationTouchpointService]
 */
sealed interface NotificationTouchpoint {
  val touchpointId: String
  val formattedDisplayValue: String

  data class PhoneNumberTouchpoint(
    override val touchpointId: String,
    val value: PhoneNumber,
  ) : NotificationTouchpoint {
    override val formattedDisplayValue: String = value.formattedDisplayValue
  }

  data class EmailTouchpoint(
    override val touchpointId: String,
    val value: Email,
  ) : NotificationTouchpoint {
    override val formattedDisplayValue: String = value.value
  }
}
