package bitkey.securitycenter

import bitkey.notifications.NotificationsService
import build.wallet.account.AccountService
import build.wallet.availability.AppFunctionalityService
import build.wallet.availability.FunctionalityFeatureStates
import build.wallet.bitkey.account.FullAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart

interface CriticalAlertsActionFactory {
  suspend fun create(): Flow<SecurityAction?>
}

@BitkeyInject(AppScope::class)
class CriticalAlertsActionFactoryImpl(
  private val accountService: AccountService,
  private val notificationsService: NotificationsService,
  private val appFunctionalityService: AppFunctionalityService,
) : CriticalAlertsActionFactory {
  override suspend fun create(): Flow<SecurityAction?> {
    return combine(
      accountService.activeAccount(),
      notificationsService.getCriticalNotificationStatus(),
      appFunctionalityService.status
    ) { account, notificationStatus, featureState ->
      if (account !is FullAccount) {
        null
      } else {
        CriticalAlertsAction(
          notificationStatus = notificationStatus,
          featureState = featureState.featureStates.notifications
        )
      }
    }.onStart {
      emit(
        CriticalAlertsAction(
          notificationStatus = NotificationsService.NotificationStatus.Enabled,
          featureState = FunctionalityFeatureStates.FeatureState.Unavailable
        )
      )
    }
  }
}
