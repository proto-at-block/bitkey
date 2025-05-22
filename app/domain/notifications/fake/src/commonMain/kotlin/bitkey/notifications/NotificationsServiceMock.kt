package bitkey.notifications

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class NotificationsServiceMock : NotificationsService {
  val criticalNotificationsStatus = MutableStateFlow<NotificationsService.NotificationStatus>(
    NotificationsService.NotificationStatus.Enabled
  )

  override fun getCriticalNotificationStatus(): Flow<NotificationsService.NotificationStatus> {
    return criticalNotificationsStatus
  }

  fun reset() {
    criticalNotificationsStatus.value = NotificationsService.NotificationStatus.Enabled
  }
}
