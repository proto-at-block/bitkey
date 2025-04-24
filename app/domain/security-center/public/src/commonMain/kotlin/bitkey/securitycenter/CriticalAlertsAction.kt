package bitkey.securitycenter

import bitkey.notifications.NotificationChannel
import bitkey.notifications.NotificationsService

class CriticalAlertsAction(
  private val notificationStatus: NotificationsService.NotificationStatus,
) : SecurityAction {
  override fun getRecommendations(): List<SecurityActionRecommendation> =
    when (notificationStatus) {
      is NotificationsService.NotificationStatus.Enabled -> {
        emptyList()
      }
      is NotificationsService.NotificationStatus.Missing -> {
        notificationStatus.missingChannels.map { it.toSecurityActionRecommendation() }
      }
      else -> {
        listOf(SecurityActionRecommendation.ENABLE_CRITICAL_ALERTS)
      }
    }

  override fun category(): SecurityActionCategory = SecurityActionCategory.RECOVERY

  override fun type(): SecurityActionType = SecurityActionType.CRITICAL_ALERTS
}

private fun NotificationChannel.toSecurityActionRecommendation(): SecurityActionRecommendation =
  when (this) {
    NotificationChannel.Email -> SecurityActionRecommendation.ENABLE_EMAIL_NOTIFICATIONS
    NotificationChannel.Push -> SecurityActionRecommendation.ENABLE_PUSH_NOTIFICATIONS
    NotificationChannel.Sms -> SecurityActionRecommendation.ENABLE_SMS_NOTIFICATIONS
  }
