package bitkey.notifications

import build.wallet.bitkey.f8e.AccountId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class NotificationsServiceMock : NotificationsService {
  val criticalNotificationsStatus = MutableStateFlow<NotificationsService.NotificationStatus>(
    NotificationsService.NotificationStatus.Enabled
  )

  override fun getCriticalNotificationStatus(
    accountId: AccountId,
  ): Flow<NotificationsService.NotificationStatus> {
    return criticalNotificationsStatus
  }

  fun reset() {
    criticalNotificationsStatus.value = NotificationsService.NotificationStatus.Enabled
  }
}
