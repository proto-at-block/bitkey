package build.wallet.statemachine.settings.full.notifications

import build.wallet.bitkey.f8e.AccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.notifications.NotificationChannel
import build.wallet.platform.permissions.Permission
import build.wallet.platform.permissions.PermissionChecker
import build.wallet.platform.permissions.PermissionStatus
import build.wallet.platform.settings.TelephonyCountryCodeProvider
import build.wallet.platform.settings.isCountry
import build.wallet.statemachine.notifications.NotificationsPreferencesCachedProvider
import com.github.michaelbull.result.get
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@BitkeyInject(
  scope = AppScope::class,
  boundTypes = [NotificationsService::class]
)
class NotificationsServiceImpl(
  private val permissionChecker: PermissionChecker,
  private val notificationsPreferencesProvider: NotificationsPreferencesCachedProvider,
  private val telephonyCountryCodeProvider: TelephonyCountryCodeProvider,
) : NotificationsService {
  override fun getCriticalNotificationStatus(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
  ): Flow<NotificationsService.NotificationStatus> =
    notificationsPreferencesProvider
      .getNotificationsPreferences(
        f8eEnvironment = f8eEnvironment,
        accountId = accountId
      ).map { preferences ->
        val isUsCountryCode = telephonyCountryCodeProvider.isCountry("us")
        val criticalChannels = setOfNotNull(
          NotificationChannel.Sms.takeIf { !isUsCountryCode },
          NotificationChannel.Push,
          NotificationChannel.Email
        )
        val notificationPermission = permissionChecker.getPermissionStatus(Permission.PushNotifications)
        val missingChannels = criticalChannels
          .minus(preferences.get()?.accountSecurity.orEmpty())
          .let {
            if (notificationPermission != PermissionStatus.Authorized) {
              it + NotificationChannel.Push
            } else {
              it
            }
          }

        when {
          preferences.isErr -> NotificationsService.NotificationStatus.Error(preferences.error)
          missingChannels.isNotEmpty() -> NotificationsService.NotificationStatus.Missing(missingChannels)
          notificationPermission != PermissionStatus.Authorized -> NotificationsService.NotificationStatus.Missing(
            setOf(NotificationChannel.Push)
          )
          else -> NotificationsService.NotificationStatus.Enabled
        }
      }
}
