package bitkey.securitycenter

import bitkey.notifications.NotificationsService
import build.wallet.account.AccountService
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logError
import kotlinx.coroutines.flow.first

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
}

interface CriticalAlertsActionFactory {
  suspend fun create(): SecurityAction?
}

@BitkeyInject(AppScope::class)
class CriticalAlertsActionFactoryImpl(
  private val accountService: AccountService,
  private val notificationsService: NotificationsService,
) : CriticalAlertsActionFactory {
  override suspend fun create(): SecurityAction? {
    val account = accountService.activeAccount().first()
    if (account == null) {
      logError { "No active account found when checking critical alerts." }
      return null
    }
    val notificationStatus = notificationsService.getCriticalNotificationStatus(account.accountId).first()
    return CriticalAlertsAction(notificationStatus)
  }
}
