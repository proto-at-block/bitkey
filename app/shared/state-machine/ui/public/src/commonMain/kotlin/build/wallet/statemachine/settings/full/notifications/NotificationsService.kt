package build.wallet.statemachine.settings.full.notifications

import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.notifications.NotificationChannel
import kotlinx.coroutines.flow.Flow

/**
 * Provides information and actions related to notification preferences in the app.
 */
interface NotificationsService {
  /**
   * Get the current state of the user's preferences for alerts that are
   * considered critical to the application.
   */
  fun getCriticalNotificationStatus(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
  ): Flow<NotificationStatus>

  /**
   * State of a collection of application notification channels.
   */
  sealed interface NotificationStatus {
    /**
     * All channels possible are enabled by the user.
     */
    data object Enabled : NotificationStatus

    /**
     * Some potential notification channels are not enabled by the user.
     */
    data class Missing(val missingChannels: Set<NotificationChannel>) : NotificationStatus

    /**
     * Unexpected error ocurred while attempting to load the notification status.
     */
    data class Error(val cause: Throwable) : NotificationStatus
  }
}
