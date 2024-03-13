package build.wallet.statemachine.platform.permissions

/**
 * Reason for requesting notification permissions.
 *
 * This is used to change the message displayed to the user to explain
 * why we are asking for notification permissions.
 */
enum class NotificationRationale {
  /**
   * Generic request for notification permissions.
   */
  Generic,

  /**
   * Request for notification permissions after a recovery event.
   */
  Recovery,
}
