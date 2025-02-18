package build.wallet.statemachine.full.notifications

import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.statemachine.settings.full.notifications.NotificationsService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class NotificationsServiceMock : NotificationsService {
  val criticalNotificationsStatus = MutableStateFlow<NotificationsService.NotificationStatus>(
    NotificationsService.NotificationStatus.Enabled
  )

  override fun getCriticalNotificationStatus(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
  ): Flow<NotificationsService.NotificationStatus> {
    return criticalNotificationsStatus
  }

  fun reset() {
    criticalNotificationsStatus.value = NotificationsService.NotificationStatus.Enabled
  }
}
