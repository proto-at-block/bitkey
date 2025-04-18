package bitkey.securitycenter

import bitkey.notifications.NotificationsService
import build.wallet.account.AccountService
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

interface CriticalAlertsActionFactory {
  suspend fun create(): Flow<SecurityAction?>
}

@BitkeyInject(AppScope::class)
class CriticalAlertsActionFactoryImpl(
  private val accountService: AccountService,
  private val notificationsService: NotificationsService,
) : CriticalAlertsActionFactory {
  override suspend fun create(): Flow<SecurityAction?> {
    val account = accountService.activeAccount().first()
    if (account == null) {
      logError { "No active account found when checking critical alerts." }
      return flowOf(null)
    }
    return notificationsService.getCriticalNotificationStatus(account.accountId).map {
      CriticalAlertsAction(it)
    }
  }
}
