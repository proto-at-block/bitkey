package bitkey.securitycenter

import bitkey.notifications.NotificationsService

class CriticalAlertsAction(
  private val notificationStatus: NotificationsService.NotificationStatus,
) : SecurityAction {
  override fun getRecommendations(): List<SecurityActionRecommendation> =
    when (notificationStatus) {
      is NotificationsService.NotificationStatus.Enabled -> {
        emptyList()
      }
      else -> {
        listOf(SecurityActionRecommendation.ENABLE_CRITICAL_ALERTS)
      }
    }

  override fun category(): SecurityActionCategory = SecurityActionCategory.RECOVERY

  override fun type(): SecurityActionType = SecurityActionType.CRITICAL_ALERTS
}
