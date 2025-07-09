package bitkey.securitycenter

import bitkey.notifications.NotificationChannel
import bitkey.notifications.NotificationsService
import build.wallet.availability.FunctionalityFeatureStates

class CriticalAlertsAction(
  private val notificationStatus: NotificationsService.NotificationStatus,
  private val featureState: FunctionalityFeatureStates.FeatureState,
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

  override fun state(): SecurityActionState {
    return if (featureState != FunctionalityFeatureStates.FeatureState.Available) {
      SecurityActionState.Disabled
    } else if (
      notificationStatus is NotificationsService.NotificationStatus.Missing &&
      notificationStatus.missingChannels.contains(NotificationChannel.Email)
    ) {
      SecurityActionState.HasCriticalActions
    } else if (getRecommendations().isNotEmpty()) {
      SecurityActionState.HasRecommendationActions
    } else {
      SecurityActionState.Secure
    }
  }
}

private fun NotificationChannel.toSecurityActionRecommendation(): SecurityActionRecommendation =
  when (this) {
    NotificationChannel.Email -> SecurityActionRecommendation.ENABLE_EMAIL_NOTIFICATIONS
    NotificationChannel.Push -> SecurityActionRecommendation.ENABLE_PUSH_NOTIFICATIONS
    NotificationChannel.Sms -> SecurityActionRecommendation.ENABLE_SMS_NOTIFICATIONS
  }
