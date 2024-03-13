package build.wallet.notifications

import build.wallet.logging.LogLevel
import build.wallet.logging.log

/**
 * Represents the notification channels the user has selected for each category of notifications.
 *
 * moneyMovement - Transactions. Push is the only option for this category.
 * productMarketing - Bitkey feature messages and feedback. Push and email are available.
 * accountSecurity - Recovery options and security-related messages. Sms is not an option for
 * US-based users.
 */
data class NotificationPreferences(
  val moneyMovement: Set<NotificationChannel>,
  val productMarketing: Set<NotificationChannel>,
  val accountSecurity: Set<NotificationChannel> = emptySet(),
)

/**
 * The form of notification selected.
 */
enum class NotificationChannel {
  Email,
  Push,
  Sms,
  ;

  companion object {
    /**
     * If in the future the server defines new NotificationChannel options, safely avoid
     * the exception thrown when calling valueOf.
     */
    fun valueOfOrNull(v: String): NotificationChannel? =
      try {
        valueOf(v)
      } catch (e: IllegalArgumentException) {
        // This should only happen if a new channel was sent from the server
        log(level = LogLevel.Info, throwable = e) { "NotificationChannel for string \"$v\" not found" }
        null
      }
  }
}
