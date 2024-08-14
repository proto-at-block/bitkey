package build.wallet.notifications

import kotlinx.coroutines.flow.Flow

/**
 * Manages interactions with a user's notification touchpoints, such as email or phone number.
 */
interface NotificationTouchpointService {
  /**
   * Returns the latest [NotificationTouchpointData] stored in the app's database.
   */
  fun notificationTouchpointData(): Flow<NotificationTouchpointData>
}
