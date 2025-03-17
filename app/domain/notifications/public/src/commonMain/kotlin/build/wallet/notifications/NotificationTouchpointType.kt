package build.wallet.notifications

/**
 * Represents different types of notification touchpoints for a customer's account that
 * can be added to the account using [NotificationTouchpointService]
 */
enum class NotificationTouchpointType {
  /** Corresponds to [NotificationTouchpoint.PhoneNumberTouchpoint] */
  PhoneNumber,

  /** Corresponds to [NotificationTouchpoint.EmailTouchpoint] */
  Email,
}
